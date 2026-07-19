package com.litecut.app.timeline

data class SnapGuide(
    val timeSeconds: Double,
    val type: SnapTargetType,
    val label: String,
    val sourceId: String? = null,
    val layerId: String? = null,
    val edgeSnapped: String? = null, // "start" or "end" of the dragged/trimmed object
    val snapLineColor: Int = 0xFFFF2D55.toInt(), // Default nice red
    val distanceHintSeconds: Double = 0.0
)
