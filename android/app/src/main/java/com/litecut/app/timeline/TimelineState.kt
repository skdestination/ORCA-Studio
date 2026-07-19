package com.litecut.app.timeline

data class TimelineState(
    val tracks: List<TimelineTrack> = emptyList(),
    val clips: List<TimelineClip> = emptyList(),
    val currentTime: Double = 0.0,
    val zoomLevel: Double = 1.0,
    val scrollX: Double = 0.0,
    val scrollY: Double = 0.0,
    val selectedClipIds: Set<String> = emptySet(),
    val isPlaying: Boolean = false
)
