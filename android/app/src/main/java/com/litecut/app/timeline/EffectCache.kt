package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap

class EffectCache private constructor(private val context: Context) : ManagedCache {
    override val categoryName: String = "effect_cache"

    // Simulates cached heavy effect state (e.g. pre-calculated blur convolution weights,
    // noise textures, bloom kernel buffers, face tracking coordinates, etc.)
    private val cachedWeights = ConcurrentHashMap<String, FloatArray>()
    private val cachedMetadata = ConcurrentHashMap<String, String>()

    companion object {
        @Volatile
        private var instance: EffectCache? = null

        fun getInstance(context: Context? = null): EffectCache {
            return instance ?: synchronized(this) {
                instance ?: if (context != null) {
                    EffectCache(context.applicationContext).also {
                        instance = it
                        // Register with central ResourceManager
                        ResourceManager.getInstance(it.context).registerCache(it.categoryName, it)
                    }
                } else {
                    throw IllegalStateException("EffectCache is not initialized. Please pass a valid Context first.")
                }
            }
        }
    }

    fun getWeights(key: String): FloatArray? {
        return cachedWeights[key]
    }

    fun putWeights(key: String, weights: FloatArray) {
        cachedWeights[key] = weights
    }

    fun getMetadata(key: String): String? {
        return cachedMetadata[key]
    }

    fun putMetadata(key: String, metadata: String) {
        cachedMetadata[key] = metadata
    }

    override fun getCurrentSizeBytes(): Long {
        var sizeBytes = 0L
        for (arr in cachedWeights.values) {
            sizeBytes += arr.size * 4L // Float is 4 bytes
        }
        for ((k, v) in cachedMetadata) {
            sizeBytes += k.length * 2L + v.length * 2L // Char is 2 bytes
        }
        return sizeBytes
    }

    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        val weightKeys = cachedWeights.keys()
        while (weightKeys.hasMoreElements()) {
            if (freed >= bytesToFree) break
            val key = weightKeys.nextElement()
            val arr = cachedWeights.remove(key)
            if (arr != null) {
                freed += arr.size * 4L
            }
        }
        
        val metaKeys = cachedMetadata.keys()
        while (metaKeys.hasMoreElements()) {
            if (freed >= bytesToFree) break
            val key = metaKeys.nextElement()
            val metaStr = cachedMetadata.remove(key)
            if (metaStr != null) {
                freed += key.length * 2L + metaStr.length * 2L
            }
        }
        Log.d("EffectCache", "EffectCache trimmed memory. Freed: $freed bytes")
    }

    override fun clear() {
        cachedWeights.clear()
        cachedMetadata.clear()
        Log.i("EffectCache", "EffectCache cleared successfully.")
    }
}
