package com.litecut.app.timeline

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import kotlin.math.max

/**
 * Interface allowing observers to handle frame rendering clock ticks.
 */
interface PreviewSchedulerListener {
    fun onFrameTick(frameTimeNanos: Long, deltaSeconds: Double, currentSeconds: Double)
}

/**
 * Handles vsync-synchronized frame scheduling using Choreographer and binds timing directly to a PlaybackClock.
 * Implements adaptive performance scaling by evaluating actual vsync jitter on every frame and adjusting
 * quality scales dynamically (e.g. for heavy 4K HDR playback).
 */
class PreviewScheduler(
    private val clock: PlaybackClock,
    private val listener: PreviewSchedulerListener
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    
    @Volatile
    private var isRunning = false
    
    private var lastFrameTimeNanos = 0L
    private var targetFps = 60.0 // Ready for up to 120 FPS
    private var targetFrameDurationNanos = (1_000_000_000.0 / targetFps).toLong()
    
    // Performance measurement metrics
    private var frameCount = 0
    private var totalFrameDelayNanos = 0L
    private var lastMetricsResetTimeMs = 0L
    
    var adaptiveQualityScale = 1.0f
        private set

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameTimeNanos == 0L) {
                lastFrameTimeNanos = frameTimeNanos
                scheduleNextCallback()
                return
            }

            val elapsedNanos = frameTimeNanos - lastFrameTimeNanos
            
            // Frame rate alignment filter
            if (elapsedNanos >= targetFrameDurationNanos - 1_000_000L) { // 1ms buffer tolerance
                val deltaSeconds = elapsedNanos.toDouble() / 1_000_000_000.0
                val currentTime = clock.getTimeSeconds()
                
                // Keep track of frames and jitter for scaling engine
                trackPerformance(elapsedNanos)
                
                listener.onFrameTick(frameTimeNanos, deltaSeconds, currentTime)
                lastFrameTimeNanos = frameTimeNanos
            }

            scheduleNextCallback()
        }
    }

    private fun scheduleNextCallback() {
        if (isRunning) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun setTargetFps(fps: Double) {
        synchronized(this) {
            targetFps = fps.coerceIn(24.0, 120.0)
            targetFrameDurationNanos = (1_000_000_000.0 / targetFps).toLong()
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameTimeNanos = 0L
        lastMetricsResetTimeMs = System.currentTimeMillis()
        frameCount = 0
        totalFrameDelayNanos = 0L
        adaptiveQualityScale = 1.0f

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            mainHandler.post {
                if (isRunning) {
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } else {
            mainHandler.post {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
        }
    }

    private fun trackPerformance(elapsedNanos: Long) {
        frameCount++
        val expectedNanos = targetFrameDurationNanos
        val delay = max(0L, elapsedNanos - expectedNanos)
        totalFrameDelayNanos += delay

        val now = System.currentTimeMillis()
        if (now - lastMetricsResetTimeMs >= 1000) { // Evaluate every 1 second
            val avgDelayMs = (totalFrameDelayNanos / frameCount) / 1_000_000.0
            
            // Adaptively scale visual quality down if frame presentation is bottlenecked
            adaptiveQualityScale = when {
                avgDelayMs > 16.0 -> 0.50f // Drop rendering surface resolution to 50% under extreme loads
                avgDelayMs > 8.0 -> 0.75f  // Drop rendering surface resolution to 75%
                avgDelayMs < 2.0 -> 1.00f  // Restore full resolution
                else -> adaptiveQualityScale
            }

            frameCount = 0
            totalFrameDelayNanos = 0L
            lastMetricsResetTimeMs = now
        }
    }
}
