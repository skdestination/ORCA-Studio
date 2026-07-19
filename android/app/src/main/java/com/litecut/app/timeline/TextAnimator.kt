package com.litecut.app.timeline

object TextAnimator {
    /**
     * Evaluates the animated typography, transformation, styling, and letter-spacing parameters
     * of a TextLayer at the given relative playback offset.
     * Operates in-place on the TextDocument instance to achieve zero garbage collector overhead at 120 FPS.
     */
    fun evaluate(
        clip: Clip,
        textLayer: TextLayer,
        relativeTimeOffset: Double
    ) {
        if (!textLayer.isEnabled) return

        val id = textLayer.id
        val doc = textLayer.document

        // Helper to evaluate float properties
        fun evaluateFloat(propName: String, fallback: Float): Float {
            val animPropName = "text_${id}_${propName}"
            val previousFallback = clip.additionalProperties[animPropName]
            clip.additionalProperties[animPropName] = fallback
            
            val evaluatedValue = AnimationEvaluator.evaluate(
                clip = clip,
                property = animPropName,
                timeOffset = relativeTimeOffset
            ).toFloat()

            if (previousFallback != null) {
                clip.additionalProperties[animPropName] = previousFallback
            } else {
                clip.additionalProperties.remove(animPropName)
            }
            return evaluatedValue
        }

        // 1. Transform properties
        doc.translationX = evaluateFloat("translateX", doc.translationX)
        doc.translationY = evaluateFloat("translateY", doc.translationY)
        doc.scaleX = evaluateFloat("scaleX", doc.scaleX)
        doc.scaleY = evaluateFloat("scaleY", doc.scaleY)
        doc.rotation = evaluateFloat("rotation", doc.rotation)
        doc.opacity = evaluateFloat("opacity", doc.opacity).coerceIn(0.0f, 1.0f)

        // 2. Main root style properties
        val rootStyle = doc.rootStyle
        rootStyle.fontSize = evaluateFloat("fontSize", rootStyle.fontSize).coerceAtLeast(1.0f)
        rootStyle.letterSpacing = evaluateFloat("letterSpacing", rootStyle.letterSpacing)
        rootStyle.lineHeight = evaluateFloat("lineHeight", rootStyle.lineHeight).coerceAtLeast(0.0f)
        rootStyle.strokeWidth = evaluateFloat("strokeWidth", rootStyle.strokeWidth).coerceAtLeast(0.0f)
        rootStyle.shadowOffsetX = evaluateFloat("shadowOffsetX", rootStyle.shadowOffsetX)
        rootStyle.shadowOffsetY = evaluateFloat("shadowOffsetY", rootStyle.shadowOffsetY)
        rootStyle.shadowRadius = evaluateFloat("shadowRadius", rootStyle.shadowRadius).coerceAtLeast(0.0f)
        rootStyle.backgroundPadding = evaluateFloat("backgroundPadding", rootStyle.backgroundPadding).coerceAtLeast(0.0f)

        // 3. For Word-level / Character-level, animated captions, or karaoke text:
        // We can evaluate dynamic custom parameters that determine segment visibility or karaoke highlight range
        val karaokeProgress = evaluateFloat("karaokeProgress", -1.0f)
        if (karaokeProgress >= 0.0f) {
            clip.additionalProperties["text_${id}_active_karaoke_progress"] = karaokeProgress
        }
    }
}
