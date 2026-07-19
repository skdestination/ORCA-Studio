package com.litecut.app.timeline

import kotlin.math.abs

class SnapManager {
    var snapThresholdSeconds: Double = 0.15
    var isSnappingEnabled: Boolean = true
        set(value) {
            field = value
            SnapEngine.getInstance().settings.isEnabled = value
        }

    init {
        SnapEngine.getInstance().settings.isEnabled = isSnappingEnabled
    }

    /**
     * Legacy compatibility bridge method. Delegates to SnapEngine or calculates snapping dynamically.
     */
    fun findSnapPoint(
        proposedTime: Double,
        clips: List<TimelineClip>,
        excludeClipId: String,
        currentTime: Double,
        markers: List<Double> = emptyList()
    ): Double {
        if (!isSnappingEnabled) return proposedTime

        // Configure engine markers dynamically
        val snapEngine = SnapEngine.getInstance()
        if (markers.isNotEmpty()) {
            snapEngine.setTimelineMarkers(markers)
        }

        // Gather all static targets manually for this standalone/legacy non-drag request
        val targets = ArrayList<SnapTarget>()
        
        // Playhead
        targets.add(SnapTarget(SnapTargetType.PLAYHEAD, currentTime, "Playhead"))
        // Timeline boundary
        targets.add(SnapTarget(SnapTargetType.TIMELINE_START, 0.0, "Timeline Start"))
        
        // Markers
        for (i in markers.indices) {
            targets.add(SnapTarget(SnapTargetType.MARKER, markers[i], "Marker ${i + 1}"))
        }

        // Add clip starts and ends
        for (clip in clips) {
            if (clip.id == excludeClipId) continue
            targets.add(SnapTarget(SnapTargetType.CLIP_START, clip.startTime, "Clip Start", clip.id))
            targets.add(SnapTarget(SnapTargetType.CLIP_END, clip.startTime + clip.duration, "Clip End", clip.id))
        }

        val result = SnapCalculator.findBestSnap(
            proposedTime = proposedTime,
            candidates = targets,
            thresholdSeconds = snapThresholdSeconds
        )

        return if (result.isSnapped) result.snappedTimeSeconds else proposedTime
    }
}
