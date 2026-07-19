package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.audio.WaveformEngine
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import com.litecut.app.timeline.tasks.CancellationToken
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import com.litecut.app.timeline.thumbnail.ThumbnailEngine
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Interface allowing plugins to register custom asset types and handlers.
 */
interface AssetPlugin {
    val typeName: String
    fun onImport(entry: AssetEntry)
}

class AssetManager private constructor(private val context: Context) {

    val database = AssetDatabase()
    val scanner = AssetScanner(context)
    val dependencyGraph = AssetDependencyGraph()
    private val extractor = AssetMetadataExtractor(context)

    // Plugin registry
    private val plugins = CopyOnWriteArrayList<AssetPlugin>()

    // Track recently used assets (in-memory)
    private val recentlyUsedAssetIds = CopyOnWriteArrayList<String>()

    // Subsystem memory caches managed by ResourceManager
    lateinit var metadataCache: GenericManagedCache<String, AssetEntry>
    lateinit var previewCache: GenericManagedCache<String, ByteArray>
    lateinit var iconCache: GenericManagedCache<String, ByteArray>
    lateinit var proxyCache: GenericManagedCache<String, String>
    lateinit var aiCache: GenericManagedCache<String, Map<String, Any?>>

    companion object {
        private const val TAG = "AssetManager"

        @Volatile
        private var instance: AssetManager? = null

        fun getInstance(context: Context): AssetManager {
            return instance ?: synchronized(this) {
                instance ?: AssetManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        // Load existing database from disk
        database.load(context)

        // Initialize and register all caches with the central ResourceManager
        setupResourceManagerCaches()
    }

    private fun setupResourceManagerCaches() {
        val resourceManager = ResourceManager.getInstance(context)

        // LRU sizes in bytes
        metadataCache = GenericManagedCache("metadata", 1024 * 1024 * 5) { 512 } // 5MB limit
        previewCache = GenericManagedCache("preview_cache", 1024 * 1024 * 20) { it.size.toLong() } // 20MB limit
        iconCache = GenericManagedCache("icon_cache", 1024 * 1024 * 5) { it.size.toLong() } // 5MB limit
        proxyCache = GenericManagedCache("proxy_cache", 1024 * 1024 * 2) { it.length.toLong() } // 2MB limit
        aiCache = GenericManagedCache("ai_cache", 1024 * 1024 * 10) { 1024 } // 10MB limit

        resourceManager.registerCache("metadata", metadataCache)
        resourceManager.registerCache("preview_cache", previewCache)
        resourceManager.registerCache("icon_cache", iconCache)
        resourceManager.registerCache("proxy_cache", proxyCache)
        resourceManager.registerCache("ai_cache", aiCache)
    }

    /**
     * Registers an external plugin to handle custom asset formats.
     */
    fun registerPlugin(plugin: AssetPlugin) {
        plugins.add(plugin)
        Log.i(TAG, "Registered custom asset plugin for type: ${plugin.typeName}")
    }

    /**
     * Unregisters a plugin.
     */
    fun unregisterPlugin(plugin: AssetPlugin) {
        plugins.remove(plugin)
    }

    /**
     * Core method to retrieve an asset with ZERO allocations and lock-free reads.
     * Perfect for hot-path rendering and playback loops.
     */
    fun getAssetPlaybackPath(id: String): AssetEntry? {
        return database.get(id)
    }

    /**
     * Imports a media asset into ORCA. Performs SHA-256 duplicate checking,
     * background metadata extraction, database indexing, persistence, and pre-generates
     * waveforms/thumbnails using the TaskScheduler.
     */
    fun importAsset(
        filePath: String,
        customType: AssetType = AssetType.UNKNOWN,
        customTypeName: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        onComplete: (AssetEntry) -> Unit = {}
    ) {
        val scheduler = TaskScheduler.getInstance(context)
        
        scheduler.submit(
            name = "AssetImport-${File(filePath).name}",
            priority = priority
        ) { token, progress ->
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                Log.e(TAG, "Cannot import non-existent file path: $filePath")
                return@submit false
            }

            // 1. Calculate SHA-256 Checksum for Duplicate Detection
            progress(10)
            val checksum = calculateSHA256(file, token)
            if (token.isCancelled) return@submit false

            // Check if checksum already exists in database
            val existing = database.getAll().find { it.checksum == checksum && it.checksum.isNotEmpty() }
            if (existing != null) {
                Log.i(TAG, "Duplicate asset detected by SHA-256: ${file.name} is identical to ${existing.assetName}")
                onComplete(existing)
                return@submit true
            }

            // 2. Instantiate and Populate Base AssetEntry
            val id = "asset-${UUID.randomUUID()}"
            val detectedType = if (customType != AssetType.UNKNOWN) customType else scanner.detectAssetType(file)
            
            val entry = AssetEntry(
                id = id,
                filePath = filePath,
                originalPath = filePath,
                assetName = file.name,
                fileSize = file.length(),
                checksum = checksum,
                type = detectedType,
                customTypeName = customTypeName,
                importDateMs = System.currentTimeMillis()
            )

            // 3. Extract Metadata via Background Parser
            progress(40)
            extractor.extract(entry)
            if (token.isCancelled) return@submit false

            // 4. Custom Plugin Handling Hook
            if (entry.type == AssetType.CUSTOM || customTypeName != null) {
                val matchingPlugin = plugins.find { it.typeName == customTypeName || it.typeName == entry.customTypeName }
                matchingPlugin?.onImport(entry)
            }

            // 5. Index and Save to Database
            progress(70)
            database.put(entry)
            database.save(context)

            // 6. Integrate with Waveform and Thumbnail Engines
            progress(90)
            triggerPrefabrication(entry)

            onComplete(entry)
            Log.i(TAG, "Successfully imported asset '${entry.assetName}' with ID $id")
            true
        }
    }

    /**
     * Performs a batch import of multiple media files in parallel.
     */
    fun importMultipleAssets(
        filePaths: List<String>,
        priority: TaskPriority = TaskPriority.NORMAL,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: (List<AssetEntry>) -> Unit = {}
    ) {
        val results = CopyOnWriteArrayList<AssetEntry>()
        var completedCount = 0
        val totalCount = filePaths.size

        if (totalCount == 0) {
            onComplete(emptyList())
            return
        }

        for (path in filePaths) {
            importAsset(path, priority = priority) { entry ->
                results.add(entry)
                completedCount++
                onProgress(completedCount, totalCount)
                if (completedCount == totalCount) {
                    onComplete(results.toList())
                }
            }
        }
    }

    /**
     * Increments the project usage tracker for an asset.
     */
    fun recordAssetUsage(projectId: String, clipId: String, assetId: String) {
        dependencyGraph.registerUsage(projectId, clipId, assetId)
        val entry = database.get(assetId)
        if (entry != null) {
            entry.usageCount = dependencyGraph.getClipsUsingAsset(assetId).size
            database.put(entry)
            recentlyUsedAssetIds.remove(assetId)
            recentlyUsedAssetIds.add(0, assetId) // Push to top
        }
    }

    /**
     * Decrements the project usage tracker for an asset.
     */
    fun recordAssetUnused(clipId: String) {
        val assetId = dependencyGraph.getAssetForClip(clipId)
        dependencyGraph.unregisterUsage(clipId)
        if (assetId != null) {
            val entry = database.get(assetId)
            if (entry != null) {
                entry.usageCount = dependencyGraph.getClipsUsingAsset(assetId).size
                database.put(entry)
            }
        }
    }

    /**
     * Scans for assets registered in the database that are no longer used by any clips in any projects.
     */
    fun findOrphanAssets(): List<AssetEntry> {
        return database.getAll().filter { dependencyGraph.isSafeToDelete(it.id) }
    }

    fun getRecentlyImported(limit: Int = 10): List<AssetEntry> {
        return database.getAll().sortedByDescending { it.importDateMs }.take(limit)
    }

    fun getRecentlyUsed(limit: Int = 10): List<AssetEntry> {
        return recentlyUsedAssetIds.mapNotNull { database.get(it) }.take(limit)
    }

    /**
     * Toggles the favorite status of an asset.
     */
    fun setFavorite(id: String, isFavorite: Boolean) {
        val entry = database.get(id) ?: return
        entry.isFavorite = isFavorite
        database.put(entry)
        database.save(context)
    }

    /**
     * Updates custom tags on an asset.
     */
    fun updateTags(id: String, tags: Set<String>) {
        val entry = database.get(id) ?: return
        entry.tags.clear()
        entry.tags.addAll(tags)
        database.put(entry)
        database.save(context)
    }

    /**
     * Updates collections list of an asset.
     */
    fun updateCollections(id: String, collections: Set<String>) {
        val entry = database.get(id) ?: return
        entry.collections.clear()
        entry.collections.addAll(collections)
        database.put(entry)
        database.save(context)
    }

    /**
     * Scans recursively to relink offline assets.
     */
    fun relinkOfflineAssets(searchDirectory: File, onAssetRelinked: (AssetEntry, String) -> Unit = { _, _ -> }) {
        val missing = database.getAll().filter {
            val file = File(it.filePath)
            !file.exists() || !file.isFile
        }

        if (missing.isEmpty()) return

        scanner.scanDirectory(searchDirectory, priority = TaskPriority.HIGH) { files ->
            var count = 0
            for (asset in missing) {
                val originalName = File(asset.originalPath).name
                val match = files.find { it.name.equals(originalName, ignoreCase = true) }
                if (match != null) {
                    asset.filePath = match.absolutePath
                    database.put(asset)
                    count++
                    onAssetRelinked(asset, match.absolutePath)
                }
            }
            if (count > 0) {
                database.save(context)
                Log.i(TAG, "Relinked $count assets automatically.")
            }
        }
    }

    /**
     * Deletes an asset entry from the database.
     */
    fun deleteAsset(id: String, deleteSourceFile: Boolean = false): Boolean {
        val entry = database.remove(id) ?: return false
        if (deleteSourceFile) {
            try {
                val file = File(entry.filePath)
                if (file.exists()) file.delete()
            } catch (ignored: Exception) {}
        }
        database.save(context)
        Log.i(TAG, "Deleted asset $id from database.")
        return true
    }

    /**
     * Triggers Waveform pre-extraction and Cover Thumbnail generation automatically in background.
     */
    private fun triggerPrefabrication(entry: AssetEntry) {
        try {
            if (entry.type == AssetType.AUDIO) {
                val dummyClip = Clip(
                    id = entry.id,
                    layerId = "prefab_layer",
                    type = ClipType.AUDIO,
                    src = entry.filePath,
                    leftSeconds = 0.0,
                    durationSeconds = entry.audioDurationMs / 1000.0,
                    trimStartSeconds = 0.0
                )
                WaveformEngine.getInstance(context).requestWaveformAsynchronously(dummyClip) {
                    Log.d(TAG, "Auto-prebuilt audio waveform for imported asset: ${entry.assetName}")
                }
            } else if (entry.type == AssetType.VIDEO) {
                val dummyClip = Clip(
                    id = entry.id,
                    layerId = "prefab_layer",
                    type = ClipType.VIDEO,
                    src = entry.filePath,
                    leftSeconds = 0.0,
                    durationSeconds = entry.videoDurationMs / 1000.0,
                    trimStartSeconds = 0.0
                )
                ThumbnailEngine.getInstance(context).requestThumbnail(dummyClip, 0.0, 128, 128) {
                    Log.d(TAG, "Auto-prebuilt cover frame thumbnail for imported video: ${entry.assetName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed pre-extracting waveforms/thumbnails for asset: ${entry.assetName}", e)
        }
    }

    private fun calculateSHA256(file: File, token: CancellationToken): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    if (token.isCancelled) return ""
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest()
            val hexString = java.lang.StringBuilder()
            for (b in hash) {
                hexString.append(String.format("%02x", b))
            }
            hexString.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed calculating SHA-256", e)
            ""
        }
    }
}

/**
 * Generic ManagedCache implementation bridging memory storage with the ResourceManager.
 */
class GenericManagedCache<K, V>(
    override val categoryName: String,
    private val maxSizeBytes: Long,
    private val sizeEstimator: (V) -> Long
) : ManagedCache {
    
    private val cache = object : java.util.LinkedHashMap<K, V>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>?): Boolean {
            if (currentSize > maxSizeBytes) {
                if (eldest != null) {
                    val size = sizeEstimator(eldest.value)
                    currentSize -= size
                    return true
                }
            }
            return false
        }
    }

    @Volatile
    private var currentSize = 0L

    override fun getCurrentSizeBytes(): Long = currentSize

    @Synchronized
    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        val iterator = cache.entries.iterator()
        while (iterator.hasNext() && freed < bytesToFree) {
            val entry = iterator.next()
            val size = sizeEstimator(entry.value)
            freed += size
            currentSize -= size
            iterator.remove()
        }
    }

    @Synchronized
    override fun clear() {
        cache.clear()
        currentSize = 0L
    }

    @Synchronized
    fun get(key: K): V? = cache[key]

    @Synchronized
    fun put(key: K, value: V) {
        val size = sizeEstimator(value)
        val old = cache.put(key, value)
        if (old != null) {
            currentSize -= sizeEstimator(old)
        }
        currentSize += size
    }
}
