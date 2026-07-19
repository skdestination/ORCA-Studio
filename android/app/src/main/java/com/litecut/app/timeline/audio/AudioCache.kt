package com.litecut.app.timeline.audio

import com.litecut.app.timeline.resources.ManagedCache
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Metadata descriptor for cached PCM segments.
 */
class CachedAudioSegment(
    val clipId: String,
    val segmentIndex: Int,
    val data: FloatArray, // Interleaved or concatenated PCM data
    val sampleRate: Int,
    val channels: Int
) {
    val sizeBytes: Long = data.size * 4L
    @Volatile
    var lastAccessedTime: Long = System.currentTimeMillis()
    @Volatile
    var isPinned: Boolean = false
}

/**
 * Thread-safe LRU cache for pre-decoded PCM segments.
 * Prevents real-time filesystem loading on the high-priority audio threads.
 */
class AudioCache private constructor() : ManagedCache {

    override val categoryName: String = "audio_pcm_cache"

    private val cache = ConcurrentHashMap<String, CachedAudioSegment>()
    private val totalCachedBytes = AtomicLong(0)
    
    // Default budget: 32MB of pre-decoded PCM segments in cache
    private var maxMemoryBudget = 32L * 1024L * 1024L 

    companion object {
        @Volatile
        private var instance: AudioCache? = null

        fun getInstance(): AudioCache {
            return instance ?: synchronized(this) {
                instance ?: AudioCache().also { instance = it }
            }
        }
    }

    /**
     * Gets a cached segment if it exists, updating its LRU timestamp.
     */
    fun get(clipId: String, segmentIndex: Int): CachedAudioSegment? {
        val key = makeKey(clipId, segmentIndex)
        val segment = cache[key]
        if (segment != null) {
            segment.lastAccessedTime = System.currentTimeMillis()
        }
        return segment
    }

    /**
     * Places a decoded PCM segment in cache, evicting cold entries if budget is exceeded.
     */
    fun put(segment: CachedAudioSegment) {
        val key = makeKey(segment.clipId, segment.segmentIndex)
        
        synchronized(this) {
            val existing = cache.remove(key)
            if (existing != null) {
                totalCachedBytes.addAndGet(-existing.sizeBytes)
            }

            // Verify memory budget before adding
            ensureCapacity(segment.sizeBytes)
            
            cache[key] = segment
            totalCachedBytes.addAndGet(segment.sizeBytes)
        }
    }

    /**
     * Pins a segment so it is excluded from LRU automatic eviction.
     */
    fun pin(clipId: String, segmentIndex: Int, pin: Boolean) {
        val key = makeKey(clipId, segmentIndex)
        cache[key]?.isPinned = pin
    }

    private fun makeKey(clipId: String, segmentIndex: Int): String {
        return "${clipId}_segment_$segmentIndex"
    }

    private fun ensureCapacity(neededBytes: Long) {
        while (totalCachedBytes.get() + neededBytes > maxMemoryBudget) {
            val evicted = evictLruEntry()
            if (!evicted) break // Nothing left to evict
        }
    }

    private fun evictLruEntry(): Boolean {
        var oldestKey: String? = null
        var oldestTime = Long.MAX_VALUE
        var oldestSegment: CachedAudioSegment? = null

        for ((key, segment) in cache) {
            if (!segment.isPinned && segment.lastAccessedTime < oldestTime) {
                oldestTime = segment.lastAccessedTime
                oldestKey = key
                oldestSegment = segment
            }
        }

        if (oldestKey != null && oldestSegment != null) {
            cache.remove(oldestKey)
            totalCachedBytes.addAndGet(-oldestSegment.sizeBytes)
            return true
        }
        return false
    }

    fun setBudget(bytes: Long) {
        maxMemoryBudget = bytes
        trimMemory(0)
    }

    // --- ManagedCache ---

    override fun getCurrentSizeBytes(): Long {
        return totalCachedBytes.get()
    }

    override fun trimMemory(bytesToFree: Long) {
        synchronized(this) {
            var freed = 0L
            while (freed < bytesToFree || totalCachedBytes.get() > maxMemoryBudget) {
                var oldestKey: String? = null
                var oldestTime = Long.MAX_VALUE
                var oldestSegment: CachedAudioSegment? = null

                for ((key, segment) in cache) {
                    if (!segment.isPinned && segment.lastAccessedTime < oldestTime) {
                        oldestTime = segment.lastAccessedTime
                        oldestKey = key
                        oldestSegment = segment
                    }
                }

                if (oldestKey != null && oldestSegment != null) {
                    cache.remove(oldestKey)
                    totalCachedBytes.addAndGet(-oldestSegment.sizeBytes)
                    freed += oldestSegment.sizeBytes
                } else {
                    break // All remaining entries are pinned or cache is empty
                }
            }
        }
    }

    override fun clear() {
        synchronized(this) {
            cache.clear()
            totalCachedBytes.set(0)
        }
    }
}
