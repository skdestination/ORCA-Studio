package com.litecut.app.timeline

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap

class FontCache private constructor(private var context: Context?) : ManagedCache {
    override val categoryName: String = "font_cache"

    // Thread-safe map storing compiled Typeface elements
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    companion object {
        @Volatile
        private var instance: FontCache? = null

        fun getInstance(context: Context? = null): FontCache {
            val ctx = context?.applicationContext ?: ApplicationContextProvider.context
            return instance?.apply {
                if (ctx != null && this.context == null) {
                    this.context = ctx
                    try {
                        ResourceManager.getInstance(ctx).registerCache(categoryName, this)
                    } catch (e: Exception) {
                        Log.w("FontCache", "Failed to register cache with ResourceManager", e)
                    }
                }
            } ?: synchronized(this) {
                instance ?: FontCache(ctx).also {
                    instance = it
                    if (ctx != null) {
                        try {
                            ResourceManager.getInstance(ctx).registerCache(it.categoryName, it)
                        } catch (e: Exception) {
                            Log.w("FontCache", "Failed to register cache with ResourceManager", e)
                        }
                    }
                }
            }
        }
    }

    fun getTypeface(fontKey: String): Typeface? {
        return typefaceCache[fontKey]
    }

    fun putTypeface(fontKey: String, typeface: Typeface) {
        typefaceCache[fontKey] = typeface
    }

    override fun getCurrentSizeBytes(): Long {
        // Since Typeface sizes are native-allocated on Android and difficult to measure directly,
        // we estimate size based on metadata or count of fonts loaded.
        return typefaceCache.size * 512 * 1024L // Generic estimate: 512KB per typeface
    }

    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        val keys = typefaceCache.keys()
        while (keys.hasMoreElements()) {
            if (freed >= bytesToFree) break
            val key = keys.nextElement()
            // Keep default system fonts
            if (key != "sans-serif" && key != "serif" && key != "monospace") {
                typefaceCache.remove(key)
                freed += 512 * 1024L
            }
        }
        Log.d("FontCache", "FontCache trimmed memory. Freed: $freed bytes")
    }

    override fun clear() {
        typefaceCache.clear()
        Log.i("FontCache", "FontCache cleared successfully.")
    }
}
