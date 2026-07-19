package com.litecut.app.timeline

import kotlin.math.abs

object BezierCurve {
    /**
     * Solves for y given x on a cubic Bezier curve defined by control points (x1, y1) and (x2, y2).
     * P0 is assumed to be (0, 0) and P3 is assumed to be (1, 1).
     */
    fun evaluate(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0

        // Find t for given x using Newton-Raphson
        var t = x
        for (i in 0..8) {
            val sampleX = getX(t, x1, x2)
            val slope = getSlope(t, x1, x2)
            if (slope == 0.0) break
            val diff = sampleX - x
            t -= diff / slope
            if (abs(diff) < 1e-6) break
        }
        
        // Fallback to binary search if Newton-Raphson didn't converge perfectly
        if (abs(getX(t, x1, x2) - x) > 1e-4) {
            var tLower = 0.0
            var tUpper = 1.0
            t = x

            while (tLower < tUpper) {
                val sampleX = getX(t, x1, x2)
                if (abs(sampleX - x) < 1e-5) break
                if (x > sampleX) {
                    tLower = t
                } else {
                    tUpper = t
                }
                t = (tUpper + tLower) * 0.5
            }
        }

        return getY(t, y1, y2)
    }

    private fun getX(t: Double, x1: Double, x2: Double): Double {
        return 3.0 * (1.0 - t) * (1.0 - t) * t * x1 + 3.0 * (1.0 - t) * t * t * x2 + t * t * t
    }

    private fun getY(t: Double, y1: Double, y2: Double): Double {
        return 3.0 * (1.0 - t) * (1.0 - t) * t * y1 + 3.0 * (1.0 - t) * t * t * y2 + t * t * t
    }

    private fun getSlope(t: Double, x1: Double, x2: Double): Double {
        return 3.0 * (1.0 - t) * (1.0 - t) * x1 + 6.0 * (1.0 - t) * t * (x2 - x1) + 3.0 * t * t * (1.0 - x2)
    }
}
