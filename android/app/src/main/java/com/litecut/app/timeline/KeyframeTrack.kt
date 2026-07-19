package com.litecut.app.timeline

import java.util.Collections

class KeyframeTrack(val property: String) {
    private val keyframes = ArrayList<Keyframe>()

    @Synchronized
    fun getKeyframes(): List<Keyframe> = keyframes.toList()

    @Synchronized
    fun addKeyframe(kf: Keyframe) {
        // Remove existing keyframe at exactly the same timeOffset to avoid duplication
        keyframes.removeAll { it.timeOffset == kf.timeOffset && it.property == kf.property }
        keyframes.add(kf)
        keyframes.sortBy { it.timeOffset }
    }

    @Synchronized
    fun removeKeyframe(id: String): Boolean {
        return keyframes.removeAll { it.id == id }
    }

    @Synchronized
    fun removeKeyframeAt(timeOffset: Double): Boolean {
        return keyframes.removeAll { it.timeOffset == timeOffset }
    }

    @Synchronized
    fun clear() {
        keyframes.clear()
    }

    /**
     * Find surrounding keyframes for evaluation using binary search O(log n).
     * Returns a Pair of Keyframes (Left, Right) around the target time.
     * If time is before the first keyframe, returns (First, First).
     * If time is after the last keyframe, returns (Last, Last).
     * If no keyframes exist, returns (null, null).
     */
    @Synchronized
    fun findSurroundingKeyframes(timeOffset: Double): Pair<Keyframe?, Keyframe?> {
        if (keyframes.isEmpty()) return Pair(null, null)
        if (keyframes.size == 1) return Pair(keyframes[0], keyframes[0])

        if (timeOffset <= keyframes[0].timeOffset) {
            return Pair(keyframes[0], keyframes[0])
        }
        if (timeOffset >= keyframes.last().timeOffset) {
            return Pair(keyframes.last(), keyframes.last())
        }

        // Binary search
        var low = 0
        var high = keyframes.size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTime = keyframes[mid].timeOffset

            when {
                midTime == timeOffset -> return Pair(keyframes[mid], keyframes[mid])
                midTime < timeOffset -> low = mid + 1
                else -> high = mid - 1
            }
        }

        // 'high' is the element just before timeOffset, 'low' is the element just after
        return Pair(keyframes[high], keyframes[low])
    }
}
