package com.litecut.app.timeline

object EffectEvaluator {
    /**
     * Evaluates the active animated parameters of an Effect at the current relative offset.
     * Modifies the parameters in-place to ensure zero-allocation runtime execution during 120 FPS playback.
     */
    fun evaluate(
        clip: Clip,
        effect: Effect,
        relativeTimeOffset: Double
    ) {
        if (!effect.isEnabled) return

        for ((paramName, param) in effect.parameters) {
            // Check for animatable property named: effect_<effectId>_<paramName>
            val animPropName = "effect_${effect.id}_${paramName}"
            
            // Temporary insertion of static fallback value into additionalProperties 
            // so AnimationEvaluator.getStaticFallbackValue has a proper fallback if no keyframes are defined yet.
            val previousPropertyFallback = clip.additionalProperties[animPropName]
            clip.additionalProperties[animPropName] = param.value
            
            val evaluatedValue = AnimationEvaluator.evaluate(
                clip = clip,
                property = animPropName,
                timeOffset = relativeTimeOffset
            ).toFloat()

            // Restore the original property if needed (or keep it as fallback)
            if (previousPropertyFallback != null) {
                clip.additionalProperties[animPropName] = previousPropertyFallback
            } else {
                clip.additionalProperties.remove(animPropName)
            }

            // Clamp evaluated value inside parameter bounds
            param.value = evaluatedValue.coerceIn(param.minValue, param.maxValue)
        }
    }

    /**
     * Evaluates an entire stack of effects sequentially.
     */
    fun evaluateStack(
        clip: Clip,
        stack: EffectStack,
        relativeTimeOffset: Double
    ) {
        for (effect in stack.effects) {
            evaluate(clip, effect, relativeTimeOffset)
        }
    }
}
