package com.litecut.app.timeline

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RenderStatistics {
    var lastFrameRenderTimeNs: Long = 0L
    private val frameCount = AtomicLong(0)
    private val totalRenderTimeNs = AtomicLong(0)
    private val maxRenderTimeNs = AtomicLong(0)
    private val textureCacheHits = AtomicLong(0)
    private val textureCacheMisses = AtomicLong(0)
    private val activeFboCount = AtomicInteger(0)
    private val activeTextureCount = AtomicInteger(0)
    private val drawCalls = AtomicInteger(0)

    fun recordFrame(renderTimeNs: Long) {
        lastFrameRenderTimeNs = renderTimeNs
        frameCount.incrementAndGet()
        totalRenderTimeNs.addAndGet(renderTimeNs)
        var currentMax = maxRenderTimeNs.get()
        while (renderTimeNs > currentMax) {
            if (maxRenderTimeNs.compareAndSet(currentMax, renderTimeNs)) {
                break
            }
            currentMax = maxRenderTimeNs.get()
        }
    }

    fun recordTextureHit() = textureCacheHits.incrementAndGet()
    fun recordTextureMiss() = textureCacheMisses.incrementAndGet()
    
    fun setFboCount(count: Int) = activeFboCount.set(count)
    fun setTextureCount(count: Int) = activeTextureCount.set(count)
    
    fun recordDrawCall() = drawCalls.incrementAndGet()
    fun resetDrawCalls() = drawCalls.set(0)

    fun getAverageRenderTimeMs(): Double {
        val frames = frameCount.get()
        if (frames == 0L) return 0.0
        return (totalRenderTimeNs.get() / frames) / 1_000_000.0
    }

    fun getMaxRenderTimeMs(): Double = maxRenderTimeNs.get() / 1_000_000.0
    fun getTextureHitRate(): Double {
        val hits = textureCacheHits.get()
        val total = hits + textureCacheMisses.get()
        if (total == 0L) return 1.0
        return hits.toDouble() / total.toDouble()
    }

    fun getActiveFboCount(): Int = activeFboCount.get()
    fun getActiveTextureCount(): Int = activeTextureCount.get()
    fun getDrawCallsPerFrame(): Int = drawCalls.get()

    fun reset() {
        frameCount.set(0)
        totalRenderTimeNs.set(0)
        maxRenderTimeNs.set(0)
        textureCacheHits.set(0)
        textureCacheMisses.set(0)
        activeFboCount.set(0)
        activeTextureCount.set(0)
        drawCalls.set(0)
    }
}
