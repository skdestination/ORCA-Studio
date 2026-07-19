package com.litecut.app.timeline

class PlaybackClock {
    private var baseTimeSeconds: Double = 0.0
    private var lastUpdateNanoTime: Long = 0L
    private var speed: Double = 1.0
    private var isPlaying: Boolean = false
    private var maxDurationSeconds: Double = 3600.0 // 1 Hour default
    private var driftCorrectionSeconds: Double = 0.0

    @Synchronized
    fun start(startTimeSeconds: Double) {
        baseTimeSeconds = startTimeSeconds
        lastUpdateNanoTime = System.nanoTime()
        driftCorrectionSeconds = 0.0
        isPlaying = true
    }

    @Synchronized
    fun pause() {
        if (isPlaying) {
            baseTimeSeconds = getTimeSeconds()
            driftCorrectionSeconds = 0.0
            isPlaying = false
        }
    }

    @Synchronized
    fun stop() {
        baseTimeSeconds = 0.0
        driftCorrectionSeconds = 0.0
        isPlaying = false
    }

    @Synchronized
    fun setTime(seconds: Double) {
        baseTimeSeconds = seconds.coerceIn(0.0, maxDurationSeconds)
        lastUpdateNanoTime = System.nanoTime()
        driftCorrectionSeconds = 0.0
    }

    @Synchronized
    fun setSpeed(newSpeed: Double) {
        if (speed != newSpeed) {
            // Commit current time under the old speed as the new base
            baseTimeSeconds = getTimeSeconds()
            lastUpdateNanoTime = System.nanoTime()
            speed = newSpeed
        }
    }

    @Synchronized
    fun getSpeed(): Double = speed

    @Synchronized
    fun isRunning(): Boolean = isPlaying

    @Synchronized
    fun setMaxDuration(durationSeconds: Double) {
        maxDurationSeconds = durationSeconds
    }

    /**
     * Micro-correction to keep video sync with audio clock.
     * Can be called dynamically from Audio Sync monitor.
     */
    @Synchronized
    fun applyDriftCorrection(correctionSeconds: Double) {
        driftCorrectionSeconds += correctionSeconds
    }

    /**
     * Retrieves high-precision time in seconds.
     * Calculated as: baseTimeSeconds + (elapsedTime * speed) + driftCorrectionSeconds
     */
    @Synchronized
    fun getTimeSeconds(): Double {
        if (!isPlaying) {
            return baseTimeSeconds.coerceIn(0.0, maxDurationSeconds)
        }
        val currentNano = System.nanoTime()
        val elapsedNano = currentNano - lastUpdateNanoTime
        val elapsedSeconds = elapsedNano.toDouble() / 1_000_000_000.0
        
        val calculated = baseTimeSeconds + (elapsedSeconds * speed) + driftCorrectionSeconds
        return calculated.coerceIn(0.0, maxDurationSeconds)
    }

    @Synchronized
    fun reset() {
        baseTimeSeconds = 0.0
        lastUpdateNanoTime = 0L
        speed = 1.0
        isPlaying = false
        driftCorrectionSeconds = 0.0
    }
}
