package com.litecut.app.timeline

data class CompositionContext(
    val currentTime: Double,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val isProxyMode: Boolean = false,
    val playbackSpeed: Double = 1.0,
    val isExporting: Boolean = false
)
