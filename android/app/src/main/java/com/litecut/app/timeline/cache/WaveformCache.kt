package com.litecut.app.timeline.cache

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.audio.WaveformData
import com.litecut.app.timeline.resources.CacheEntry
import com.litecut.app.timeline.resources.CachePolicy
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache implementation specifically for WaveformData.
 * Integrates with central ResourceManager for LRU memory budgeting,
 * while supporting pinning for currently visible viewport clips to protect them from eviction.
 */
class WaveformCache(private val context: Context) : ManagedCache {

    override val categoryName: String = "waveform"

    private val cache = ConcurrentHashMap<String, CacheEntry<WaveformData>>()
    private val pinnedClipIds = ConcurrentHashMap.newKeySet<String>()

    init {
        // Register with the central ResourceManager
        ResourceManager.getInstance(context).registerCache(categoryName, this)
    }

    override fun getCurrentSizeBytes(): Long {
        var total = 0L
        for (entry in cache.values) {
            total += entry.sizeBytes
        }
        return total
    }

    /**
     * Trims old waveforms until required memory is freed.
     * Respects pinned visible clips, ensuring they are not evicted.
     */
    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        val sortedEntries = cache.values
            .filter { !pinnedClipIds.contains(it.key) }
            .sortedBy { it.lastAccessedAt }
            
        val resourceManager = ResourceManager.getInstance(context)

        for (entry in sortedEntries) {
            if (freed >= bytesToFree) break
            if (cache.remove(entry.key) != null) {
                freed += entry.sizeBytes
                resourceManager.stats.incrementEvictions()
            }
        }
        Log.d("WaveformCache", "trimMemory: Freed ${freed / 1024} KB of waveform cache. Pinned count: ${pinnedClipIds.size}")
    }

    fun pinWaveform(clipId: String) {
        pinnedClipIds.add(clipId)
    }

    fun unpinWaveform(clipId: String) {
        pinnedClipIds.remove(clipId)
    }

    fun clearPinned() {
        pinnedClipIds.clear()
    }

    fun getWaveform(clipId: String): WaveformData? {
        val resourceManager = ResourceManager.getInstance(context)
        val entry = cache[clipId]
        if (entry != null) {
            entry.lastAccessedAt = System.currentTimeMillis()
            resourceManager.stats.incrementHits()
            return entry.value
        }
        resourceManager.stats.incrementMisses()
        return null
    }

    fun putWaveform(clipId: String, data: WaveformData) {
        val sizeBytes = data.sizeBytes()
        val entry = CacheEntry(clipId, data, sizeBytes, CachePolicy.LRU)
        cache[clipId] = entry

        // Enforce the budget limit check with ResourceManager
        ResourceManager.getInstance(context).checkAndEnforceBudget(categoryName)
    }

    override fun clear() {
        cache.clear()
        pinnedClipIds.clear()
        ResourceManager.getInstance(context).updateTotalMemoryUsageStats()
    }
}
