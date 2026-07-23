package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import org.json.JSONObject
import java.io.File

class ProjectEngine private constructor(private var context: Context?) {

    var currentDocument: ProjectDocument? = null
        private set
        
    var currentProjectFile: File? = null
        private set

    val autoSaveManager = AutoSaveManager(context) { this }
    val backupManager = BackupManager(context)
    private val compressor = ProjectCompressor()
    private val serializer = TimelineSerializer()
    private val deserializer = TimelineDeserializer()
    private val versionManager = VersionManager()
    private val integrityChecker = IntegrityChecker()

    companion object {
        @Volatile
        private var instance: ProjectEngine? = null

        fun getInstance(context: Context? = null): ProjectEngine {
            val ctx = context?.applicationContext ?: ApplicationContextProvider.context
            return instance?.apply {
                if (ctx != null && this.context == null) {
                    this.context = ctx
                    this.autoSaveManager.updateContext(ctx)
                    this.backupManager.updateContext(ctx)
                }
            } ?: synchronized(this) {
                instance ?: ProjectEngine(ctx).also { instance = it }
            }
        }
    }

    /**
     * Initializes a new, empty project session inside ORCA.
     */
    fun createNewProject(projectName: String): ProjectDocument {
        closeCurrentProject()
        
        val doc = ProjectDocument()
        doc.metadata.name = projectName
        doc.metadata.createdAtMs = System.currentTimeMillis()
        doc.metadata.modifiedAtMs = System.currentTimeMillis()
        
        currentDocument = doc
        autoSaveManager.clearDirty()
        
        Log.i("ProjectEngine", "Initialized new project document: $projectName")
        return doc
    }

    /**
     * Saves the active timeline project synchronously to the current project file.
     * Triggers version checks, creates backup rotations, and clears the autosave dirty flag.
     */
    fun saveProject(targetFile: File): Boolean {
        val timelineEngine = TimelineEngine.getInstance()
        
        // 1. Compile document structure from current engine state
        val doc = serializer.serialize(timelineEngine, context)
        
        // Preserve current document identity if opening/editing existing
        currentDocument?.let { currentDoc ->
            doc.metadata.createdAtMs = currentDoc.metadata.createdAtMs
            // Keep assets linked previously
            for (oldAsset in currentDoc.assets) {
                val matchingNew = doc.assets.find { it.id == oldAsset.id || it.filePath == oldAsset.filePath }
                if (matchingNew == null) {
                    doc.assets.add(oldAsset)
                }
            }
        }
        
        doc.metadata.name = targetFile.nameWithoutExtension
        doc.metadata.modifiedAtMs = System.currentTimeMillis()
        
        // 2. Perform file backup rotation
        if (targetFile.exists()) {
            backupManager.createProjectBackup(targetFile)
        }

        // 3. Compress and write
        val success = saveProjectDocumentToFile(doc, targetFile)
        if (success) {
            currentDocument = doc
            currentProjectFile = targetFile
            autoSaveManager.clearDirty()
            Log.i("ProjectEngine", "Project saved successfully to: ${targetFile.absolutePath}")
        }
        return success
    }

    /**
     * Internal low-level file compression writer.
     */
    internal fun saveProjectDocumentToFile(doc: ProjectDocument, file: File): Boolean {
        val jsonStr = doc.toJSONObject().toString()
        return compressor.compressToFile(jsonStr, file)
    }

    /**
     * Loads a project from an `.orca` file, executing file decompression, compatibility checks,
     * database migrations, and restores timeline configurations back into the engine.
     */
    fun loadProject(projectFile: File): LoadProjectResult {
        closeCurrentProject()

        val jsonStr = compressor.decompressFromFile(projectFile)
        if (jsonStr == null) {
            Log.e("ProjectEngine", "Decompression failed for: ${projectFile.absolutePath}")
            return LoadProjectResult.Failure("Corrupted project file or invalid file format.")
        }

        val jsonObject = try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            Log.e("ProjectEngine", "Failed to parse project JSON", e)
            return LoadProjectResult.Failure("Invalid project document metadata structure.")
        }

        // 1. Compatibility Check & Migrations
        val compResult = versionManager.checkCompatibility(jsonObject)
        val finalJson = if (compResult == CompatibilityResult.MIGRATION_REQUIRED) {
            versionManager.migrate(jsonObject)
        } else {
            jsonObject
        }

        if (compResult == CompatibilityResult.FORWARD_COMPATIBILITY_WARNING) {
            Log.w("ProjectEngine", "Loading a project from a newer version of ORCA.")
        }

        // 2. Load Project Document Object
        val doc = try {
            ProjectDocument.fromJSONObject(finalJson)
        } catch (e: Exception) {
            Log.e("ProjectEngine", "Failed to build ProjectDocument model", e)
            return LoadProjectResult.Failure("Schema parsing error - cannot load project.")
        }

        // Re-resolve and update AssetReference metadata and usage from AssetManager
        val assetManager = AssetManager.getInstance(context)
        for (asset in doc.assets) {
            val dbAsset = assetManager.getAssetPlaybackPath(asset.id)
            if (dbAsset != null) {
                asset.filePath = dbAsset.filePath
                asset.originalPath = dbAsset.originalPath
                asset.assetName = dbAsset.assetName
                asset.fileSize = dbAsset.fileSize
                asset.type = dbAsset.type
            } else {
                // Auto-import back into AssetManager if missing (e.g. shared project)
                assetManager.importAsset(asset.filePath, asset.type) { imported ->
                    Log.i("ProjectEngine", "Auto-imported missing asset reference: ${imported.filePath}")
                }
            }
        }

        // 3. Audit Asset Links (Check if any footage is offline)
        val offlineAssets = integrityChecker.auditAssets(doc)
        
        // 4. De-serialize and inject into TimelineEngine
        val timelineEngine = TimelineEngine.getInstance()
        deserializer.deserialize(doc, timelineEngine)

        currentDocument = doc
        currentProjectFile = projectFile
        autoSaveManager.clearDirty()

        Log.i("ProjectEngine", "Project opened successfully: ${doc.metadata.name}")
        
        return if (offlineAssets.isNotEmpty()) {
            LoadProjectResult.SuccessWithOfflineAssets(doc, offlineAssets)
        } else {
            LoadProjectResult.Success(doc)
        }
    }

    /**
     * Handles background, asynchronous relink search triggers.
     */
    fun autoRelinkOfflineAssets(searchDirectory: File, onAssetRelinked: (AssetReference, String) -> Unit = { _, _ -> }) {
        val doc = currentDocument ?: return
        
        TaskScheduler.getInstance(context).submit(
            name = "ProjectRelinkAssets-${doc.metadata.id}",
            priority = TaskPriority.NORMAL
        ) { token, progress ->
            val relinkedCount = integrityChecker.attemptAutomaticRelinking(doc, searchDirectory) { asset, path ->
                onAssetRelinked(asset, path)
            }
            if (relinkedCount > 0) {
                // Save upgraded paths back
                currentProjectFile?.let { saveProject(it) }
                Log.i("ProjectEngine", "Auto-relinked $relinkedCount assets successfully.")
            }
            true
        }
    }

    /**
     * Discards current session parameters cleanly.
     */
    fun closeCurrentProject() {
        currentDocument = null
        currentProjectFile = null
        autoSaveManager.clearDirty()
        TimelineEngine.getInstance().clear()
        Log.d("ProjectEngine", "Closed project session.")
    }
}

sealed class LoadProjectResult {
    data class Success(val document: ProjectDocument) : LoadProjectResult()
    data class SuccessWithOfflineAssets(val document: ProjectDocument, val offlineAssets: List<AssetReference>) : LoadProjectResult()
    data class Failure(val errorMsg: String) : LoadProjectResult()
}
