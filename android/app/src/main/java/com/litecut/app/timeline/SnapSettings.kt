package com.litecut.app.timeline

import kotlin.math.max

class SnapSettings {
    var isEnabled: Boolean = true
    var targetFps: Double = 30.0
    val disabledTargetTypes = HashSet<SnapTargetType>()

    fun isTargetTypeEnabled(type: SnapTargetType): Boolean {
        return isEnabled && !disabledTargetTypes.contains(type)
    }

    /**
     * Dynamically calculates the snap threshold in seconds based on current zoom level (pixelsPerSecond).
     * Zoomed Out (low pps): larger snap radius (e.g. 20-30px) translated to time to feel responsive.
     * Zoomed In (high pps): smaller snap radius (e.g. 8px) down to frame-level precision limits.
     */
    fun getSnapThresholdSeconds(pixelsPerSecond: Double): Double {
        if (pixelsPerSecond <= 0.0) return 0.15
        
        val basePixelRadius = when {
            pixelsPerSecond < 15.0 -> 30.0
            pixelsPerSecond < 75.0 -> 20.0
            pixelsPerSecond < 250.0 -> 14.0
            else -> 8.0
        }
        
        val timeThreshold = basePixelRadius / pixelsPerSecond
        
        // Lower limit is 1 frame duration (frame-level precision)
        val minFrameDuration = 1.0 / targetFps
        return max(minFrameDuration, timeThreshold)
    }
}
