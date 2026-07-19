package com.litecut.app.timeline

data class SnapResult(
    val isSnapped: Boolean,
    val snappedTimeSeconds: Double,
    val originalTimeSeconds: Double,
    val offsetSeconds: Double,
    val target: SnapTarget?,
    val priority: SnapPriority,
    val edgeSnapped: String? = null // "start" or "end" if clipping snaps
) {
    companion object {
        val NO_SNAP = SnapResult(
            isSnapped = false,
            snappedTimeSeconds = 0.0,
            originalTimeSeconds = 0.0,
            offsetSeconds = 0.0,
            target = null,
            priority = SnapPriority.NONE,
            edgeSnapped = null
        )
    }
}
