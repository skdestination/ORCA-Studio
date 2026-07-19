package com.litecut.app.timeline

import android.content.Context
import org.json.JSONObject
import java.io.File

class TimelineSerializer {

    /**
     * Serializes the current active TimelineEngine state and compiles a complete ProjectDocument.
     */
    fun serialize(timelineEngine: TimelineEngine, context: Context): ProjectDocument {
        val document = ProjectDocument()
        
        // 1. Capture timeline structures
        val timelineJson = timelineEngine.getProjectJSON()
        document.timelineData = timelineJson
        
        // 2. Scan clips for active asset references
        val clips = timelineEngine.getAllClips()
        val assetsSet = mutableSetOf<String>()
        val assetManager = AssetManager.getInstance(context)
        
        for (clip in clips) {
            if (clip.src.isNotEmpty()) {
                val path = clip.src
                if (!assetsSet.contains(path)) {
                    assetsSet.add(path)
                    
                    val file = File(path)
                    val size = if (file.exists() && file.isFile) file.length() else 0L
                    val name = file.name
                    
                    val type = when (clip.type) {
                        ClipType.VIDEO -> AssetType.VIDEO
                        ClipType.AUDIO -> AssetType.AUDIO
                        ClipType.IMAGE -> AssetType.IMAGE
                        else -> AssetType.UNKNOWN
                    }
                    
                    // Look up registered Asset in our AssetManager database
                    val existingAsset = assetManager.database.getAll().find { 
                        it.filePath == path || it.originalPath == path 
                    }
                    val assetId = existingAsset?.id ?: "asset-${clip.id}-${System.nanoTime()}"
                    
                    // Record usage in dependency graph if registered
                    if (existingAsset != null) {
                        assetManager.recordAssetUsage(
                            projectId = document.metadata.id,
                            clipId = clip.id,
                            assetId = existingAsset.id
                        )
                    }

                    val assetRef = AssetReference(
                        id = assetId,
                        filePath = path,
                        originalPath = existingAsset?.originalPath ?: path,
                        assetName = existingAsset?.assetName ?: name,
                        fileSize = existingAsset?.fileSize ?: size,
                        type = existingAsset?.type ?: type
                    )
                    assetRef.checkStatus()
                    
                    // If file exists, we can compute checksum in a background task
                    // We preserve basic properties
                    document.assets.add(assetRef)
                }
            }
        }
        
        // 3. Populate ProjectMetadata
        val metadata = document.metadata
        metadata.name = "ORCA Project"
        metadata.durationMs = (timelineEngine.getTotalDurationSeconds() * 1000).toLong()
        metadata.modifiedAtMs = System.currentTimeMillis()
        
        // 4. Capture current playback UI states
        val playback = JSONObject()
        playback.put("currentTime", timelineEngine.currentTime)
        playback.put("zoomLevel", timelineEngine.zoomLevel)
        playback.put("scrollLeft", timelineEngine.scrollLeft)
        playback.put("pixelsPerSecond", timelineEngine.pixelsPerSecond)
        document.playbackSettings = playback
        
        // 5. Try to capture current export settings if they exist
        try {
            val exportEngine = ExportEngine.getInstance(timelineEngine, null)
            val activeSessions = exportEngine.getActiveSessions()
            if (activeSessions.isNotEmpty()) {
                document.exportSettings = activeSessions.first().settings.toJSONObject()
            }
        } catch (ignored: Exception) {}

        return document
    }
}

