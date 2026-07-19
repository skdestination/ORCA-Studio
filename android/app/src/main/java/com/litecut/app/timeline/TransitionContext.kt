package com.litecut.app.timeline

data class TransitionContext(
    val currentTime: Double,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val isProxyMode: Boolean = false,
    val customParams: Map<String, Any?> = emptyMap()
)
