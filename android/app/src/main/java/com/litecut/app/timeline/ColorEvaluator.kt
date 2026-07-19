package com.litecut.app.timeline

object ColorEvaluator {
    /**
     * Evaluates the active animated color parameters of a Clip at the current relative offset.
     * Modifies the target ColorAdjustment in-place to ensure zero-allocation runtime execution during playback.
     */
    fun evaluate(
        clip: Clip,
        relativeTimeOffset: Double,
        targetAdjustment: ColorAdjustment
    ) {
        // Resolve animatable exposure
        targetAdjustment.exposure = AnimationEvaluator.evaluate(
            clip = clip,
            property = "exposure",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable brightness
        targetAdjustment.brightness = AnimationEvaluator.evaluate(
            clip = clip,
            property = "brightness",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable contrast
        targetAdjustment.contrast = AnimationEvaluator.evaluate(
            clip = clip,
            property = "contrast",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable saturation
        targetAdjustment.saturation = AnimationEvaluator.evaluate(
            clip = clip,
            property = "saturation",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable vibrance
        targetAdjustment.vibrance = AnimationEvaluator.evaluate(
            clip = clip,
            property = "vibrance",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable temperature
        targetAdjustment.temperature = AnimationEvaluator.evaluate(
            clip = clip,
            property = "temperature",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable tint
        targetAdjustment.tint = AnimationEvaluator.evaluate(
            clip = clip,
            property = "tint",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable gamma
        targetAdjustment.gamma = AnimationEvaluator.evaluate(
            clip = clip,
            property = "gamma",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable highlights
        targetAdjustment.highlights = AnimationEvaluator.evaluate(
            clip = clip,
            property = "highlights",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable shadows
        targetAdjustment.shadows = AnimationEvaluator.evaluate(
            clip = clip,
            property = "shadows",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable whites
        targetAdjustment.whites = AnimationEvaluator.evaluate(
            clip = clip,
            property = "whites",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable blacks
        targetAdjustment.blacks = AnimationEvaluator.evaluate(
            clip = clip,
            property = "blacks",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable fade
        targetAdjustment.fade = AnimationEvaluator.evaluate(
            clip = clip,
            property = "fade",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Resolve animatable sharpen
        targetAdjustment.sharpenAmount = AnimationEvaluator.evaluate(
            clip = clip,
            property = "sharpenAmount",
            timeOffset = relativeTimeOffset
        ).toFloat()

        // Process any custom/HDR parameter channels
        val keys = ArrayList(targetAdjustment.customParameters.keys)
        for (key in keys) {
            targetAdjustment.customParameters[key] = AnimationEvaluator.evaluate(
                clip = clip,
                property = key,
                timeOffset = relativeTimeOffset
            ).toFloat()
        }
    }
}
