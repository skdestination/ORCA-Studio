package com.litecut.app.timeline.resources

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Common interface representing a cache or pool managed by the central ResourceManager.
 */
interface ManagedCache {
    val categoryName: String
    fun getCurrentSizeBytes(): Long
    fun trimMemory(bytesToFree: Long)
    fun clear()
}

class ResourceManager private constructor(context: Context) {

    // Thread-safe repository of all registered subsystem caches
    private val registeredCaches = ConcurrentHashMap<String, ManagedCache>()

    // Global performance and allocation statistics
    val stats = ResourceStats()

    // Setup total available budget. Let's allocate 20% of max VM heap.
    private val maxHeapBytes = Runtime.getRuntime().maxMemory()
    val budget = MemoryBudget((maxHeapBytes * 0.20).toLong())

    // Memory pressure receiver
    private val pressureMonitor = MemoryPressureMonitor(context) { trimLevel ->
        stats.incrementPressureEvents()
        handleSystemMemoryTrim(trimLevel)
    }

    init {
        pressureMonitor.start()
        Log.i("ResourceManager", "ORCA ResourceManager initialized. Max VM Heap: ${maxHeapBytes / (1024 * 1024)} MB. Total Cache Allocation: ${budget.totalMaxMemoryBytes / (1024 * 1024)} MB")
    }

    companion object {
        @Volatile
        private var instance: ResourceManager? = null

        fun getInstance(context: Context): ResourceManager {
            return instance ?: synchronized(this) {
                instance ?: ResourceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Register a modular cache under a category string (e.g. "thumbnail", "waveform").
     */
    fun registerCache(category: String, cache: ManagedCache) {
        registeredCaches[category] = cache
        stats.totalRegisteredCaches.set(registeredCaches.size)
        Log.d("ResourceManager", "Registered managed cache: $category")
    }

    /**
     * Unregister a modular cache.
     */
    fun unregisterCache(category: String) {
        registeredCaches.remove(category)
        stats.totalRegisteredCaches.set(registeredCaches.size)
    }

    /**
     * Returns a registered cache.
     */
    fun getCache(category: String): ManagedCache? {
        return registeredCaches[category]
    }

    /**
     * Evaluates if a registered cache has exceeded its assigned category budget,
     * and forces immediate eviction of oldest entries until it's compliant.
     */
    fun checkAndEnforceBudget(category: String) {
        val cache = registeredCaches[category] ?: return
        val currentSize = cache.getCurrentSizeBytes()
        val allowedLimit = budget.getLimitForCategory(category)

        if (currentSize > allowedLimit) {
            val bytesToFree = currentSize - allowedLimit
            Log.w("ResourceManager", "Budget exceeded for '$category' ($currentSize > $allowedLimit bytes). Evicting $bytesToFree bytes...")
            cache.trimMemory(bytesToFree)
            stats.incrementEvictions()
        }
        
        // Update stats
        updateTotalMemoryUsageStats()
    }

    /**
     * Trims all registered caches based on system memory pressure events.
     */
    private fun handleSystemMemoryTrim(level: Int) {
        Log.w("ResourceManager", "System memory pressure trigger: level $level")
        
        val fractionToFree = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> 1.0f // Free everything non-pinned
            
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> 0.5f // Free half
            
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> 0.25f // Free 25%
            
            else -> 0.15f
        }

        for ((category, cache) in registeredCaches) {
            val currentBytes = cache.getCurrentSizeBytes()
            val targetFreeBytes = (currentBytes * fractionToFree).toLong()
            if (targetFreeBytes > 0) {
                Log.i("ResourceManager", "Trimming cache '$category': freeing $targetFreeBytes bytes ($fractionToFree fraction)")
                cache.trimMemory(targetFreeBytes)
            }
        }
        
        updateTotalMemoryUsageStats()
    }

    /**
     * Recalculates exact total memory currently locked by all registered pools and caches combined.
     */
    fun updateTotalMemoryUsageStats() {
        var totalBytes = 0L
        for (cache in registeredCaches.values) {
            totalBytes += cache.getCurrentSizeBytes()
        }
        stats.currentMemoryUsageBytes.set(totalBytes)
    }

    /**
     * Force-clear all managed caches and pools to free up the system.
     */
    fun clearAll() {
        Log.i("ResourceManager", "Clearing all registered resources...")
        for (cache in registeredCaches.values) {
            cache.clear()
        }
        updateTotalMemoryUsageStats()
    }

    /**
     * Shuts down registered listeners and monitors safely.
     */
    fun shutdown() {
        pressureMonitor.stop()
        clearAll()
        registeredCaches.clear()
    }
}
