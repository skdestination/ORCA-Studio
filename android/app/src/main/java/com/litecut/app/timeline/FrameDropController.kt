package com.litecut.app.timeline

import android.util.Log

class FrameDropController(
    private val metrics: PlaybackMetrics,
    private val maxConsecutiveDrops: Int = 3
) {
    private var consecutiveDrops = 0
    private var lastDecisionTimeNs = 0L

    enum class DropDecision {
        RENDER,      // Render normally
        DROP,        // Skip rendering to catch up
        FORCE_RENDER // Late, but force render to prevent freezing
    }

    /**
     * Determines whether to drop or render a frame based on high-precision time calculations.
     * @param targetPresentationTimeSeconds The time when the frame is supposed to be presented.
     * @param currentClockTimeSeconds The current master playback clock time.
     * @param frameDurationSeconds The duration of a single frame.
     */
    @Synchronized
    fun evaluateFrame(
        targetPresentationTimeSeconds: Double,
        currentClockTimeSeconds: Double,
        frameDurationSeconds: Double
    ): DropDecision {
        val delaySeconds = currentClockTimeSeconds - targetPresentationTimeSeconds
        val thresholdSeconds = frameDurationSeconds * 1.5

        if (delaySeconds <= thresholdSeconds) {
            // Frame is on time or within acceptable latency boundaries
            consecutiveDrops = 0
            return DropDecision.RENDER
        }

        // Frame is late! Calculate decision
        return if (consecutiveDrops >= maxConsecutiveDrops) {
            // We've already dropped too many frames consecutively; force render to prevent visual freezes
            consecutiveDrops = 0
            metrics.recordStutter() // Count as a stutter since we are forcing a delayed frame
            DropDecision.FORCE_RENDER
        } else {
            consecutiveDrops++
            metrics.recordFrameDropped()
            DropDecision.DROP
        }
    }

    @Synchronized
    fun reset() {
        consecutiveDrops = 0
        lastDecisionTimeNs = 0L
    }
}
