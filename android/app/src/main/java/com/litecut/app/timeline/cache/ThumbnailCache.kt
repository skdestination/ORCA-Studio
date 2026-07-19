package com.litecut.app.timeline.cache

import android.graphics.Bitmap
import android.util.LruCache

class ThumbnailCache(maxSize: Int = 100) {
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun getThumbnail(clipId: String, timeOffsetSeconds: Double): Bitmap? {
        val key = "$clipId@$timeOffsetSeconds"
        return memoryCache.get(key)
    }

    fun putThumbnail(clipId: String, timeOffsetSeconds: Double, bitmap: Bitmap) {
        val key = "$clipId@$timeOffsetSeconds"
        memoryCache.put(key, bitmap)
    }

    fun clear() {
        memoryCache.evictAll()
    }
}
