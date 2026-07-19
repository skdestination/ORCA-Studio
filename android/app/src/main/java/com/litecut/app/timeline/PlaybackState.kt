package com.litecut.app.timeline

enum class PlaybackState {
    STOPPED,
    PLAYING,
    PAUSED,
    SEEKING,
    BUFFERING,
    ERROR
}

data class PlaybackConfiguration(
    val targetFps: Double = 30.0,
    val speed: Double = 1.0,
    val loop: Boolean = false,
    val isProxyMode: Boolean = false,
    val frameDurationSeconds: Double = 1.0 / targetFps
)
