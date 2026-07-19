package com.litecut.app.timeline

interface PlaybackListener {
    fun onPositionChanged(seconds: Double)
    fun onPlaybackStateChanged(isPlaying: Boolean)
}

class PlaybackEngine {
    var isPlaying: Boolean = false
        private set
    
    var currentTime: Double = 0.0
        private set

    var maxDurationSeconds: Double = 3600.0 // 1 hour boundary default

    private val listeners = mutableListOf<PlaybackListener>()

    fun registerListener(listener: PlaybackListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    fun play() {
        if (!isPlaying) {
            isPlaying = true
            notifyPlaybackStateChanged(true)
        }
    }

    fun pause() {
        if (isPlaying) {
            isPlaying = false
            notifyPlaybackStateChanged(false)
        }
    }

    fun seek(seconds: Double) {
        currentTime = seconds.coerceIn(0.0, maxDurationSeconds)
        notifyPositionChanged(currentTime)
    }

    fun onFrameTick(deltaSeconds: Double) {
        if (isPlaying) {
            currentTime += deltaSeconds
            if (currentTime >= maxDurationSeconds) {
                currentTime = maxDurationSeconds
                pause()
            }
            notifyPositionChanged(currentTime)
        }
    }

    private fun notifyPositionChanged(seconds: Double) {
        listeners.forEach { it.onPositionChanged(seconds) }
    }

    private fun notifyPlaybackStateChanged(playing: Boolean) {
        listeners.forEach { it.onPlaybackStateChanged(playing) }
    }
}
