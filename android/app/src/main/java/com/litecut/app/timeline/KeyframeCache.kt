package com.litecut.app.timeline

import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap

class KeyframeCache private constructor() : ManagedCache {
    override val categoryName: String = "KeyframeCache"
    
    // Key: "clipId:property:time", Value: Double (interpolated value)
    private val cache = ConcurrentHashMap<String, Double>()

    companion object {
        @Volatile
        private var instance: KeyframeCache? = null

        fun getInstance(context: android.content.Context? = null): KeyframeCache {
            return instance ?: synchronized(this) {
                instance ?: KeyframeCache().also { 
                    instance = it 
                    if (context != null) {
                        try {
                            ResourceManager.getInstance(context).registerCache(it.categoryName, it)
                        } catch (e: Exception) {
                            // Safe fallback in standalone test runs without full context active
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun get(clipId: String, property: String, timeOffset: Double): Double? {
        val key = "$clipId:$property:$timeOffset"
        return cache[key]
    }

    fun put(clipId: String, property: String, timeOffset: Double, value: Double) {
        val key = "$clipId:$property:$timeOffset"
        cache[key] = value
        
        // Simple eviction boundary constraint to respect memory limit
        if (cache.size > 10000) {
            trimMemory(512L * 128L) // Evict around 512 entries
        }
    }

    fun invalidateClip(clipId: String) {
        val prefix = "$clipId:"
        val keysToRemove = cache.keys().asSequence().filter { it.startsWith(prefix) }
        for (key in keysToRemove) {
            cache.remove(key)
        }
    }

    fun invalidateProperty(clipId: String, property: String) {
        val prefix = "$clipId:$property:"
        val keysToRemove = cache.keys().asSequence().filter { it.startsWith(prefix) }
        for (key in keysToRemove) {
            cache.remove(key)
        }
    }

    override fun getCurrentSizeBytes(): Long {
        // Approximate heap overhead: 128 bytes per entry
        return cache.size * 128L
    }

    override fun trimMemory(bytesToFree: Long) {
        val entriesToRemove = (bytesToFree / 128L).toInt().coerceAtLeast(1)
        var count = 0
        val iterator = cache.keys().iterator()
        while (iterator.hasNext() && count < entriesToRemove) {
            iterator.next()
            iterator.remove()
            count++
        }
    }

    override fun clear() {
        cache.clear()
    }
}
