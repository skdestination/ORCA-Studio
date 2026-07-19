package com.litecut.app.timeline

import kotlin.math.abs

object SnapCalculator {
    /**
     * Finds the strongest valid snap target for a proposed time offset.
     * Evaluates all candidates and filters them using priority rules.
     * Priority Order:
     * 1. PLAYHEAD
     * 2. CLIP_EDGE (CLIP_START, CLIP_END)
     * 3. MARKER
     * 4. KEYFRAME
     * 5. TIMELINE_EDGE (TIMELINE_START, TIMELINE_END)
     * 6. FUTURE_BEAT / SUBTITLE
     */
    fun findBestSnap(
        proposedTime: Double,
        candidates: List<SnapTarget>,
        thresholdSeconds: Double,
        edgeType: String? = null
    ): SnapResult {
        if (candidates.isEmpty()) return SnapResult.NO_SNAP

        var bestTarget: SnapTarget? = null
        var bestPriority = SnapPriority.NONE
        var smallestDiff = thresholdSeconds

        // Single-pass high-performance loop over candidates to avoid garbage collection pressure
        val size = candidates.size
        for (i in 0 until size) {
            val target = candidates[i]
            val diff = abs(proposedTime - target.timeSeconds)
            if (diff < thresholdSeconds) {
                val priority = SnapPriority.fromType(target.type)
                
                // Priority ordering constraint:
                // 1. Higher priority rank first.
                // 2. If same priority, the closer distance wins.
                if (priority.rank > bestPriority.rank) {
                    bestPriority = priority
                    bestTarget = target
                    smallestDiff = diff
                } else if (priority.rank == bestPriority.rank && diff < smallestDiff) {
                    bestTarget = target
                    smallestDiff = diff
                }
            }
        }

        val target = bestTarget ?: return SnapResult.NO_SNAP
        val snappedTime = target.timeSeconds
        val offset = snappedTime - proposedTime

        return SnapResult(
            isSnapped = true,
            snappedTimeSeconds = snappedTime,
            originalTimeSeconds = proposedTime,
            offsetSeconds = offset,
            target = target,
            priority = bestPriority,
            edgeSnapped = edgeType
        )
    }
}
