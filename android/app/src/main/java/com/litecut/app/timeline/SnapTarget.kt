package com.litecut.app.timeline

enum class SnapTargetType {
    PLAYHEAD,
    CLIP_START,
    CLIP_END,
    TIMELINE_START,
    TIMELINE_END,
    MARKER,
    KEYFRAME,
    BEAT_MARKER,
    SUBTITLE
}

data class SnapTarget(
    val type: SnapTargetType,
    val timeSeconds: Double,
    val label: String,
    val sourceId: String? = null,
    val layerId: String? = null
)
