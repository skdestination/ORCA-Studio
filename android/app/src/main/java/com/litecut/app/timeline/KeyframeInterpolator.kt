package com.litecut.app.timeline

import kotlin.math.pow

object KeyframeInterpolator {
    fun interpolate(time: Double, kf1: Keyframe, kf2: Keyframe): Double {
        if (kf1.timeOffset == kf2.timeOffset) return kf1.value
        if (time <= kf1.timeOffset) return kf1.value
        if (time >= kf2.timeOffset) return kf2.value

        val progress = (time - kf1.timeOffset) / (kf2.timeOffset - kf1.timeOffset)

        return when (kf1.interpolation) {
            InterpolationType.CONSTANT -> kf1.value
            InterpolationType.LINEAR -> {
                kf1.value + (kf2.value - kf1.value) * progress
            }
            InterpolationType.EASE_IN -> {
                // Quadratic easing in
                val t = progress * progress
                kf1.value + (kf2.value - kf1.value) * t
            }
            InterpolationType.EASE_OUT -> {
                // Quadratic easing out
                val t = progress * (2.0 - progress)
                kf1.value + (kf2.value - kf1.value) * t
            }
            InterpolationType.EASE_IN_OUT -> {
                // Cubic Hermite easing in-out
                val t = progress * progress * (3.0 - 2.0 * progress)
                kf1.value + (kf2.value - kf1.value) * t
            }
            InterpolationType.CUBIC -> {
                val t = progress * progress * progress
                kf1.value + (kf2.value - kf1.value) * t
            }
            InterpolationType.BEZIER -> {
                val x1 = kf1.cp1X ?: 0.25
                val y1 = kf1.cp1Y ?: 0.0
                val x2 = kf1.cp2X ?: 0.75
                val y2 = kf1.cp2Y ?: 1.0
                val t = BezierCurve.evaluate(progress, x1, y1, x2, y2)
                kf1.value + (kf2.value - kf1.value) * t
            }
        }
    }
}
