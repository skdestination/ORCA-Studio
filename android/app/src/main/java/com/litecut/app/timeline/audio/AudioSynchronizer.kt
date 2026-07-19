package com.litecut.app.timeline.audio

import com.litecut.app.timeline.PlaybackClock

/**
 * Handles playhead synchronization, reverse playback rates, speed adjustment markers,
 * and frames stepping alignments for the Audio Mixing Engine.
 */
class AudioSynchronizer(
    private val clock: PlaybackClock
) {
    /**
     * Calculates the target audio time seconds adjusted for speed scales and direction.
     * Zero-allocation.
     */
    fun calculateSyncTime(deltaSeconds: Double): Double {
        val speed = clock.getSpeed()
        val currentPlayhead = clock.getTimeSeconds()
        
        // Return adjusted offset
        return currentPlayhead + (deltaSeconds * speed)
    }

    /**
     * Aligns sample block rendering sizes based on playback speed (e.g., slow-motion speed-scaling).
     */
    fun calculateSampleBlockSize(baseSize: Int): Int {
        val speed = kotlin.math.abs(clock.getSpeed())
        if (speed == 0.0) return 0
        
        // Standard resample scaling factor calculation
        return (baseSize * speed).toInt().coerceIn(128, 2048)
    }
}
