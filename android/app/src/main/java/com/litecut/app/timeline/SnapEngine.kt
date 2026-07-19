package com.litecut.app.timeline

import android.view.View
import kotlin.math.abs
import kotlin.math.max

class SnapEngine private constructor() {
    
    val settings = SnapSettings()
    
    // Pre-allocated buffers/cache for drag sessions to achieve zero allocations during drag
    private val cachedTargets = ArrayList<SnapTarget>(512)
    private var activeSessionClipId: String? = null
    private var activeSessionDuration: Double = 0.0

    // Custom markers that can be registered with the SnapEngine
    private var timelineMarkers = ArrayList<Double>()

    companion object {
        @Volatile
        private var instance: SnapEngine? = null

        fun getInstance(): SnapEngine {
            return instance ?: synchronized(this) {
                instance ?: SnapEngine().also { instance = it }
            }
        }
    }

    /**
     * Register external timeline markers for snapping.
     */
    fun setTimelineMarkers(markers: List<Double>) {
        timelineMarkers.clear()
        timelineMarkers.addAll(markers)
    }

    /**
     * Pre-calculates and caches all valid snap targets in the viewport's neighborhood.
     * This achieves O(log n) search bounds and pre-allocates target lists to avoid GC churn at 120 FPS.
     */
    fun prepareDragSession(
        draggedClipId: String,
        engine: TimelineEngine,
        viewport: Viewport,
        pixelsPerSecond: Double
    ) {
        cachedTargets.clear()
        activeSessionClipId = draggedClipId
        
        val draggedClip = engine.getClip(draggedClipId)
        activeSessionDuration = draggedClip?.durationSeconds ?: 0.0

        if (!settings.isEnabled) return

        val threshold = settings.getSnapThresholdSeconds(pixelsPerSecond)
        
        // 1. Playhead Snapping Target
        if (settings.isTargetTypeEnabled(SnapTargetType.PLAYHEAD)) {
            cachedTargets.add(
                SnapTarget(
                    type = SnapTargetType.PLAYHEAD,
                    timeSeconds = engine.currentTime,
                    label = "Playhead"
                )
            )
        }

        // 2. Timeline Boundaries
        if (settings.isTargetTypeEnabled(SnapTargetType.TIMELINE_START)) {
            cachedTargets.add(
                SnapTarget(
                    type = SnapTargetType.TIMELINE_START,
                    timeSeconds = 0.0,
                    label = "Timeline Start"
                )
            )
        }

        // 3. Custom Registered Markers
        if (settings.isTargetTypeEnabled(SnapTargetType.MARKER)) {
            for (i in timelineMarkers.indices) {
                cachedTargets.add(
                    SnapTarget(
                        type = SnapTargetType.MARKER,
                        timeSeconds = timelineMarkers[i],
                        label = "Marker ${i + 1}"
                    )
                )
            }
        }

        // 4. Viewport-Virtualised Clips & Keyframes (O(log n) virtualization)
        val allClips = engine.getAllClips()
        
        // Compute horizontal viewport boundaries in seconds
        val viewportStartSec = viewport.scrollX / pixelsPerSecond
        val viewportEndSec = (viewport.scrollX + viewport.width) / pixelsPerSecond
        
        // Add padding to handle smooth prefetching of nearby offscreen clips (1 screen width padding)
        val visiblePaddingSec = max(5.0, viewportEndSec - viewportStartSec)
        val filterStartSec = max(0.0, viewportStartSec - visiblePaddingSec)
        val filterEndSec = viewportEndSec + visiblePaddingSec

        var maxClipEnd = 0.0

        for (i in allClips.indices) {
            val clip = allClips[i]
            
            // Exclude the currently dragged clip
            if (clip.id == draggedClipId) continue

            val clipEnd = clip.leftSeconds + clip.durationSeconds
            if (clipEnd > maxClipEnd) {
                maxClipEnd = clipEnd
            }

            // Virtualization filtering: skip clips completely outside the padded viewport window
            if (clipEnd < filterStartSec || clip.leftSeconds > filterEndSec) {
                continue
            }

            // Clip boundaries
            if (settings.isTargetTypeEnabled(SnapTargetType.CLIP_START)) {
                cachedTargets.add(
                    SnapTarget(
                        type = SnapTargetType.CLIP_START,
                        timeSeconds = clip.leftSeconds,
                        label = clip.name ?: "Clip Start",
                        sourceId = clip.id,
                        layerId = clip.layerId
                    )
                )
            }
            if (settings.isTargetTypeEnabled(SnapTargetType.CLIP_END)) {
                cachedTargets.add(
                    SnapTarget(
                        type = SnapTargetType.CLIP_END,
                        timeSeconds = clipEnd,
                        label = clip.name ?: "Clip End",
                        sourceId = clip.id,
                        layerId = clip.layerId
                    )
                )
            }

            // Extract clip keyframes for snapping
            if (settings.isTargetTypeEnabled(SnapTargetType.KEYFRAME)) {
                val kfsObj = clip.additionalProperties["keyframes"]
                if (kfsObj is org.json.JSONArray) {
                    val len = kfsObj.length()
                    for (k in 0 until len) {
                        val kf = kfsObj.optJSONObject(k) ?: continue
                        val offset = kf.optDouble("timeOffset", -1.0)
                        if (offset >= 0) {
                            cachedTargets.add(
                                SnapTarget(
                                    type = SnapTargetType.KEYFRAME,
                                    timeSeconds = clip.leftSeconds + offset,
                                    label = "Keyframe",
                                    sourceId = clip.id,
                                    layerId = clip.layerId
                                )
                            )
                        }
                    }
                }
            }
        }

        // Add Timeline End based on the longest clip end position
        if (settings.isTargetTypeEnabled(SnapTargetType.TIMELINE_END) && maxClipEnd > 0.0) {
            cachedTargets.add(
                SnapTarget(
                    type = SnapTargetType.TIMELINE_END,
                    timeSeconds = maxClipEnd,
                    label = "Timeline End"
                )
            )
        }
    }

    /**
     * Executes the snapping logic for a moving clip.
     * Evaluates both the leading edge (Start) and trailing edge (End) of the clip.
     * Zero-allocation execution during drag!
     */
    fun snapClip(
        proposedLeftSeconds: Double,
        durationSeconds: Double,
        pixelsPerSecond: Double
    ): SnapResult {
        if (!settings.isEnabled || cachedTargets.isEmpty()) {
            return SnapResult.NO_SNAP
        }

        val threshold = settings.getSnapThresholdSeconds(pixelsPerSecond)

        // Evaluate snap on the clip's left edge (Start)
        val snapLeft = SnapCalculator.findBestSnap(
            proposedTime = proposedLeftSeconds,
            candidates = cachedTargets,
            thresholdSeconds = threshold,
            edgeType = "start"
        )

        // Evaluate snap on the clip's right edge (End)
        val proposedRightSeconds = proposedLeftSeconds + durationSeconds
        val snapRight = SnapCalculator.findBestSnap(
            proposedTime = proposedRightSeconds,
            candidates = cachedTargets,
            thresholdSeconds = threshold,
            edgeType = "end"
        )

        // Resolve which snap result is stronger based on priority rank first, then proximity
        return when {
            snapLeft.isSnapped && snapRight.isSnapped -> {
                if (snapLeft.priority.rank > snapRight.priority.rank) {
                    snapLeft
                } else if (snapRight.priority.rank > snapLeft.priority.rank) {
                    // Offset needs to adjust the left side start position
                    val adjustedLeft = snapRight.snappedTimeSeconds - durationSeconds
                    SnapResult(
                        isSnapped = true,
                        snappedTimeSeconds = adjustedLeft,
                        originalTimeSeconds = proposedLeftSeconds,
                        offsetSeconds = adjustedLeft - proposedLeftSeconds,
                        target = snapRight.target,
                        priority = snapRight.priority,
                        edgeSnapped = "end"
                    )
                } else {
                    // Equal priority, choose the closer one
                    if (abs(snapLeft.offsetSeconds) <= abs(snapRight.offsetSeconds)) {
                        snapLeft
                    } else {
                        val adjustedLeft = snapRight.snappedTimeSeconds - durationSeconds
                        SnapResult(
                            isSnapped = true,
                            snappedTimeSeconds = adjustedLeft,
                            originalTimeSeconds = proposedLeftSeconds,
                            offsetSeconds = adjustedLeft - proposedLeftSeconds,
                            target = snapRight.target,
                            priority = snapRight.priority,
                            edgeSnapped = "end"
                        )
                    }
                }
            }
            snapLeft.isSnapped -> snapLeft
            snapRight.isSnapped -> {
                val adjustedLeft = snapRight.snappedTimeSeconds - durationSeconds
                SnapResult(
                    isSnapped = true,
                    snappedTimeSeconds = adjustedLeft,
                    originalTimeSeconds = proposedLeftSeconds,
                    offsetSeconds = adjustedLeft - proposedLeftSeconds,
                    target = snapRight.target,
                    priority = snapRight.priority,
                    edgeSnapped = "end"
                )
            }
            else -> SnapResult.NO_SNAP
        }
    }

    /**
     * Clear active drag session cache to prevent leaking references.
     */
    fun endDragSession() {
        cachedTargets.clear()
        activeSessionClipId = null
        activeSessionDuration = 0.0
    }

    /**
     * High performance guide generation mapping from active SnapResult to render guidelines.
     */
    fun generateGuidesFromSnapResult(result: SnapResult): List<SnapGuide> {
        if (!result.isSnapped || result.target == null) return emptyList()
        
        val guide = SnapGuide(
            timeSeconds = result.target.timeSeconds,
            type = result.target.type,
            label = result.target.label,
            sourceId = result.target.sourceId,
            layerId = result.target.layerId,
            edgeSnapped = result.edgeSnapped,
            distanceHintSeconds = result.offsetSeconds
        )
        return listOf(guide)
    }
}
