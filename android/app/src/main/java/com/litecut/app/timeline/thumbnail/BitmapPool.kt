package com.litecut.app.timeline.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import java.util.Collections
import java.util.LinkedList

class BitmapPool(
    context: Context,
    private val maxPoolSize: Int = 30
) : ManagedCache {

    override val categoryName: String = "bitmappool"

    private val pool = Collections.synchronizedList(LinkedList<Bitmap>())

    init {
        // Register this pool with the centralized ResourceManager
        ResourceManager.getInstance(context).registerCache(categoryName, this)
    }

    override fun getCurrentSizeBytes(): Long {
        synchronized(pool) {
            return pool.sumOf { it.byteCount.toLong() }
        }
    }

    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        synchronized(pool) {
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                if (freed >= bytesToFree) break
                val bitmap = iterator.next()
                freed += bitmap.byteCount
                bitmap.recycle()
                iterator.remove()
            }
        }
        Log.d("BitmapPool", "trimMemory: Freed ${freed / 1024} KB of pooled bitmaps.")
    }

    /**
     * Retrieves a reusable Bitmap from the pool that matches the specified dimensions and configuration.
     */
    fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        synchronized(pool) {
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val bitmap = iterator.next()
                if (bitmap.width == width && bitmap.height == height && bitmap.config == config) {
                    iterator.remove()
                    return bitmap
                }
            }
        }
        return null
    }

    /**
     * Puts an unused Bitmap back into the pool for future reuse.
     * Ensure the bitmap is mutable and not already recycled.
     */
    fun put(bitmap: Bitmap) {
        if (!bitmap.isMutable || bitmap.isRecycled) {
            return
        }
        
        synchronized(pool) {
            if (pool.size >= maxPoolSize) {
                val oldBitmap = pool.removeAt(0)
                oldBitmap.recycle()
            }
            pool.add(bitmap)
        }

        // Run budget enforcement check
        ResourceManager.getInstance(context).checkAndEnforceBudget(categoryName)
    }

    /**
     * Clears and recycles all bitmaps in the pool.
     */
    override fun clear() {
        synchronized(pool) {
            for (bitmap in pool) {
                bitmap.recycle()
            }
            pool.clear()
        }
    }
}
