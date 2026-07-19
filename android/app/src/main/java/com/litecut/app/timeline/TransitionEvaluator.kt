package com.litecut.app.timeline

object TransitionEvaluator {
    /**
     * Calculates the normalized progress (0.0 to 1.0) of a transition at the given playhead time.
     * Applies easing curves according to the easeType defined on the transition.
     */
    fun evaluateProgress(transition: Transition, currentTime: Double): Double {
        val halfDuration = transition.durationSeconds / 2.0
        val startTime = transition.centerTimeSeconds - halfDuration
        
        if (currentTime <= startTime) return 0.0
        if (currentTime >= startTime + transition.durationSeconds) return 1.0
        
        val linearProgress = (currentTime - startTime) / transition.durationSeconds
        val coercedProgress = linearProgress.coerceIn(0.0, 1.0)

        return when (transition.easeType) {
            InterpolationType.CONSTANT -> if (coercedProgress < 0.5) 0.0 else 1.0
            InterpolationType.LINEAR -> coercedProgress
            InterpolationType.EASE_IN -> BezierCurve.evaluate(coercedProgress, 0.42, 0.0, 1.0, 1.0)
            InterpolationType.EASE_OUT -> BezierCurve.evaluate(coercedProgress, 0.0, 0.0, 0.58, 1.0)
            InterpolationType.EASE_IN_OUT -> BezierCurve.evaluate(coercedProgress, 0.42, 0.0, 0.58, 1.0)
            InterpolationType.CUBIC, InterpolationType.BEZIER -> {
                val cp1X = (transition.additionalParams["cp1X"] as? Number)?.toDouble() ?: 0.42
                val cp1Y = (transition.additionalParams["cp1Y"] as? Number)?.toDouble() ?: 0.0
                val cp2X = (transition.additionalParams["cp2X"] as? Number)?.toDouble() ?: 0.58
                val cp2Y = (transition.additionalParams["cp2Y"] as? Number)?.toDouble() ?: 1.0
                BezierCurve.evaluate(coercedProgress, cp1X, cp1Y, cp2X, cp2Y)
            }
        }
    }
}
