package com.litecut.app.timeline

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PlaybackMetrics {
    private val totalFramesRendered = AtomicLong(0)
    private val totalFramesDropped = AtomicLong(0)
    private val totalStutters = AtomicInteger(0)
    private val totalSeeks = AtomicInteger(0)
    private val lastFrameTimeNs = AtomicLong(0)
    private val totalRenderTimeNs = AtomicLong(0)
    private val maxRenderTimeNs = AtomicLong(0)
    private val avDriftMs = AtomicLong(0)

    fun recordFrameRendered(renderTimeNs: Long) {
        totalFramesRendered.incrementAndGet()
        totalRenderTimeNs.addAndGet(renderTimeNs)
        
        var currentMax = maxRenderTimeNs.get()
        while (renderTimeNs > currentMax) {
            if (maxRenderTimeNs.compareAndSet(currentMax, renderTimeNs)) {
                break
            }
            currentMax = maxRenderTimeNs.get()
        }
    }

    fun recordFrameDropped() {
        totalFramesDropped.incrementAndGet()
    }

    fun recordStutter() {
        totalStutters.incrementAndGet()
    }

    fun recordSeek() {
        totalSeeks.incrementAndGet()
    }

    fun recordAVDrift(driftMs: Long) {
        avDriftMs.set(driftMs)
    }

    fun getFramesRendered(): Long = totalFramesRendered.get()
    fun getFramesDropped(): Long = totalFramesDropped.get()
    fun getStutterCount(): Int = totalStutters.get()
    fun getSeekCount(): Int = totalSeeks.get()
    fun getAverageRenderTimeMs(): Double {
        val rendered = totalFramesRendered.get()
        if (rendered == 0L) return 0.0
        return (totalRenderTimeNs.get() / rendered) / 1_000_000.0
    }
    fun getMaxRenderTimeMs(): Double = maxRenderTimeNs.get() / 1_000_000.0
    fun getAVDriftMs(): Long = avDriftMs.get()

    fun reset() {
        totalFramesRendered.set(0)
        totalFramesDropped.set(0)
        totalStutters.set(0)
        totalSeeks.set(0)
        lastFrameTimeNs.set(0)
        totalRenderTimeNs.set(0)
        maxRenderTimeNs.set(0)
        avDriftMs.set(0)
    }
}
