package com.litecut.app.timeline.resources

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ResourceStats {
    val totalRegisteredCaches = AtomicInteger(0)
    val currentMemoryUsageBytes = AtomicLong(0)
    val cacheHits = AtomicLong(0)
    val cacheMisses = AtomicLong(0)
    val evictionsCount = AtomicLong(0)
    val memoryPressureEvents = AtomicInteger(0)
    val activeBitmapsCount = AtomicInteger(0)
    val averageBitmapSizeBytes = AtomicLong(0)

    val hitRate: Double
        get() {
            val total = cacheHits.get() + cacheMisses.get()
            return if (total == 0L) 0.0 else (cacheHits.get().toDouble() / total.toDouble()) * 100.0
        }

    fun incrementHits() = cacheHits.incrementAndGet()
    fun incrementMisses() = cacheMisses.incrementAndGet()
    fun incrementEvictions() = evictionsCount.incrementAndGet()
    fun incrementPressureEvents() = memoryPressureEvents.incrementAndGet()

    override fun toString(): String {
        return """
            ORCA ResourceManager Stats:
            - Registered Caches: ${totalRegisteredCaches.get()}
            - Current Usage: ${currentMemoryUsageBytes.get() / (1024 * 1024)} MB
            - Hits: ${cacheHits.get()}
            - Misses: ${cacheMisses.get()}
            - Hit Rate: ${String.format("%.2f", hitRate)}%
            - Evictions: ${evictionsCount.get()}
            - Memory Pressure Incidents: ${memoryPressureEvents.get()}
            - Active Managed Bitmaps: ${activeBitmapsCount.get()}
            - Avg Bitmap Size: ${averageBitmapSizeBytes.get() / 1024} KB
        """.trimIndent()
    }
}
