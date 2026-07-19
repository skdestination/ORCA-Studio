package com.litecut.app.timeline

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

class FrameScheduler(
    private val onFrameTick: (frameTimeNanos: Long, deltaSeconds: Double) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastFrameTimeNanos: Long = 0L
    private var targetFps: Double = 30.0
    private var targetFrameDurationNanos: Long = (1_000_000_000.0 / targetFps).toLong()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameTimeNanos == 0L) {
                lastFrameTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
                return
            }

            val elapsedNanos = frameTimeNanos - lastFrameTimeNanos
            
            // Frame rate conversion filter:
            // Check if we have met or exceeded the frame-interval budget to prevent over-rendering.
            if (elapsedNanos >= targetFrameDurationNanos - 1_000_000L) { // 1ms threshold tolerance
                val deltaSeconds = elapsedNanos.toDouble() / 1_000_000_000.0
                onFrameTick(frameTimeNanos, deltaSeconds)
                lastFrameTimeNanos = frameTimeNanos
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setTargetFps(fps: Double) {
        targetFps = fps.coerceIn(1.0, 240.0)
        targetFrameDurationNanos = (1_000_000_000.0 / targetFps).toLong()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameTimeNanos = 0L
        
        // Choreographer must be started on a thread with a Looper, typically the Main Thread
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
}
