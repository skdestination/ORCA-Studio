package com.litecut.app.timeline.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.litecut.app.timeline.resources.CacheEntry
import com.litecut.app.timeline.resources.CachePolicy
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ThumbnailCache(
    private val context: Context,
    private val bitmapPool: BitmapPool
) : ManagedCache {

    override val categoryName: String = "thumbnail"

    // Thread-safe repository of current memory cache entries
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<Bitmap>>()

    private val diskCacheDir = File(context.cacheDir, "orca_thumbnails").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val ioExecutor = Executors.newSingleThreadExecutor()

    init {
        // Register this cache with the centralized ResourceManager
        ResourceManager.getInstance(context).registerCache(categoryName, this)

        // Run a background disk cleanup on start to maintain clean storage
        ioExecutor.execute {
            cleanOldDiskEntries()
        }
    }

    override fun getCurrentSizeBytes(): Long {
        var totalSize = 0L
        for (entry in memoryCache.values) {
            totalSize += entry.sizeBytes
        }
        return totalSize
    }

    /**
     * Trim memory to meet the specified target footprint.
     * Respects pinning and handles LRU, FIFO, and Temporary policies.
     */
    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        val resourceManager = ResourceManager.getInstance(context)

        // 1. First evict TEMPORARY resources
        val tempEntries = memoryCache.values
            .filter { it.policy == CachePolicy.TEMPORARY && !it.isPinned }
            .sortedBy { it.lastAccessedAt }
        
        for (entry in tempEntries) {
            if (freed >= bytesToFree) break
            if (memoryCache.remove(entry.key) != null) {
                freed += entry.sizeBytes
                bitmapPool.put(entry.value)
                resourceManager.stats.incrementEvictions()
            }
        }

        // 2. Next evict standard LRU resources
        if (freed < bytesToFree) {
            val lruEntries = memoryCache.values
                .filter { it.policy == CachePolicy.LRU && !it.isPinned }
                .sortedBy { it.lastAccessedAt }

            for (entry in lruEntries) {
                if (freed >= bytesToFree) break
                if (memoryCache.remove(entry.key) != null) {
                    freed += entry.sizeBytes
                    bitmapPool.put(entry.value)
                    resourceManager.stats.incrementEvictions()
                }
            }
        }

        // 3. Next evict FIFO resources
        if (freed < bytesToFree) {
            val fifoEntries = memoryCache.values
                .filter { it.policy == CachePolicy.FIFO && !it.isPinned }
                .sortedBy { it.createdAt }

            for (entry in fifoEntries) {
                if (freed >= bytesToFree) break
                if (memoryCache.remove(entry.key) != null) {
                    freed += entry.sizeBytes
                    bitmapPool.put(entry.value)
                    resourceManager.stats.incrementEvictions()
                }
            }
        }

        Log.d("ThumbnailCache", "trimMemory: Freed ${freed / 1024} KB of memory thumbnails.")
    }

    fun get(key: String, width: Int, height: Int): Bitmap? {
        val resourceManager = ResourceManager.getInstance(context)

        // L1 Memory Cache hit
        memoryCache[key]?.let { entry ->
            if (!entry.value.isRecycled) {
                entry.lastAccessedAt = System.currentTimeMillis()
                resourceManager.stats.incrementHits()
                return entry.value
            } else {
                memoryCache.remove(key)
            }
        }

        // L2 Disk Cache look up
        val diskFile = getDiskFile(key)
        if (diskFile.exists()) {
            try {
                val options = BitmapFactory.Options().apply {
                    inMutable = true
                    // Try to reuse an existing bitmap from the pool!
                    val recycledBitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
                    if (recycledBitmap != null && !recycledBitmap.isRecycled) {
                        inBitmap = recycledBitmap
                    }
                    inSampleSize = 1
                }
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath, options)
                if (bitmap != null) {
                    putInMemoryCache(key, bitmap, CachePolicy.LRU)
                    resourceManager.stats.incrementHits()
                    return bitmap
                }
            } catch (e: Exception) {
                // Fallback decode
                try {
                    val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                    if (bitmap != null) {
                        putInMemoryCache(key, bitmap, CachePolicy.LRU)
                        resourceManager.stats.incrementHits()
                        return bitmap
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }

        resourceManager.stats.incrementMisses()
        return null
    }

    fun put(key: String, bitmap: Bitmap) {
        put(key, bitmap, CachePolicy.LRU)
    }

    /**
     * Save a bitmap with a custom cache policy.
     */
    fun put(key: String, bitmap: Bitmap, policy: CachePolicy) {
        if (bitmap.isRecycled) return

        // 1. Put in L1 memory cache
        putInMemoryCache(key, bitmap, policy)

        // 2. Put in L2 disk cache asynchronously
        ioExecutor.execute {
            val diskFile = getDiskFile(key)
            if (!diskFile.exists()) {
                var out: FileOutputStream? = null
                try {
                    out = FileOutputStream(diskFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        out?.close()
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun putInMemoryCache(key: String, bitmap: Bitmap, policy: CachePolicy) {
        val sizeBytes = bitmap.byteCount.toLong()
        val entry = CacheEntry(key, bitmap, sizeBytes, policy)
        memoryCache[key] = entry

        // Enforce the budget limit check with ResourceManager
        ResourceManager.getInstance(context).checkAndEnforceBudget(categoryName)
    }

    /**
     * Expose pin capability on keys.
     */
    fun pin(key: String) {
        memoryCache[key]?.pin()
    }

    /**
     * Expose unpin capability on keys.
     */
    fun unpin(key: String) {
        memoryCache[key]?.unpin()
    }

    private fun getDiskFile(key: String): File {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9_@]"), "_")
        return File(diskCacheDir, "$safeKey.jpg")
    }

    private fun cleanOldDiskEntries(maxDiskSizeMb: Long = 50) {
        val files = diskCacheDir.listFiles() ?: return
        var currentSize = files.sumOf { it.length() }
        val limit = maxDiskSizeMb * 1024 * 1024

        if (currentSize > limit) {
            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                if (currentSize <= limit * 0.7) break
                val size = file.length()
                if (file.delete()) {
                    currentSize -= size
                }
            }
        }
    }

    override fun clear() {
        memoryCache.clear()
        ioExecutor.execute {
            val files = diskCacheDir.listFiles() ?: return@execute
            for (file in files) {
                file.delete()
            }
        }
    }
}
