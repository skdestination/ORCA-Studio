package com.litecut.app.timeline.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitors and aggregates real-time diagnostics of the Audio Mixer Engine.
 */
class AudioMetrics {

    // Thread-safe diagnostics
    private val totalProcessedFrames = AtomicLong(0)
    private val totalUnderruns = AtomicInteger(0)
    private val lastProcessingLatencyNs = AtomicLong(0)
    private val peakProcessingLatencyNs = AtomicLong(0)
    
    // Level tracking (Float-encoded integers to remain thread-safe)
    private val currentPeakLeft = AtomicInteger(0)
    private val currentPeakRight = AtomicInteger(0)
    private val currentRmsLeft = AtomicInteger(0)
    private val currentRmsRight = AtomicInteger(0)
    
    private val clippingDetected = AtomicBoolean(false)
    private val activeChannelCount = AtomicInteger(0)
    
    // Cache metrics
    private val cacheLookups = AtomicLong(0)
    private val cacheHits = AtomicLong(0)

    fun recordFrameProcessed(latencyNs: Long) {
        totalProcessedFrames.incrementAndGet()
        lastProcessingLatencyNs.set(latencyNs)
        
        var peak = peakProcessingLatencyNs.get()
        while (latencyNs > peak) {
            if (peakProcessingLatencyNs.compareAndSet(peak, latencyNs)) {
                break
            }
            peak = peakProcessingLatencyNs.get()
        }
    }

    fun recordUnderrun() {
        totalUnderruns.incrementAndGet()
    }

    fun recordLevels(peakL: Float, peakR: Float, rmsL: Float, rmsR: Float, clipping: Boolean) {
        currentPeakLeft.set(peakL.toRawBits())
        currentPeakRight.set(peakR.toRawBits())
        currentRmsLeft.set(rmsL.toRawBits())
        currentRmsRight.set(rmsR.toRawBits())
        if (clipping) {
            clippingDetected.set(true)
        }
    }

    fun recordCacheQuery(isHit: Boolean) {
        cacheLookups.incrementAndGet()
        if (isHit) {
            cacheHits.incrementAndGet()
        }
    }

    fun updateActiveChannels(count: Int) {
        activeChannelCount.set(count)
    }

    fun reset() {
        totalProcessedFrames.set(0)
        totalUnderruns.set(0)
        lastProcessingLatencyNs.set(0)
        peakProcessingLatencyNs.set(0)
        currentPeakLeft.set(0f.toRawBits())
        currentPeakRight.set(0f.toRawBits())
        currentRmsLeft.set(0f.toRawBits())
        currentRmsRight.set(0f.toRawBits())
        clippingDetected.set(false)
        activeChannelCount.set(0)
        cacheLookups.set(0)
        cacheHits.set(0)
    }

    // --- Diagnostic Accessors ---

    fun getProcessedFrameCount(): Long = totalProcessedFrames.get()
    fun getUnderrunCount(): Int = totalUnderruns.get()
    fun getLastLatencyMs(): Double = lastProcessingLatencyNs.get() / 1_000_000.0
    fun getPeakLatencyMs(): Double = peakProcessingLatencyNs.get() / 1_000_000.0
    
    fun getPeakLeft(): Float = Float.fromBits(currentPeakLeft.get())
    fun getPeakRight(): Float = Float.fromBits(currentPeakRight.get())
    fun getRmsLeft(): Float = Float.fromBits(currentRmsLeft.get())
    fun getRmsRight(): Float = Float.fromBits(currentRmsRight.get())
    
    fun isClipping(): Boolean = clippingDetected.getAndSet(false) // Read-and-clear latch
    fun getActiveChannels(): Int = activeChannelCount.get()
    
    fun getCacheHitRate(): Float {
        val lookups = cacheLookups.get()
        if (lookups == 0L) return 1.0f
        return cacheHits.get().toFloat() / lookups.toFloat()
    }
}
