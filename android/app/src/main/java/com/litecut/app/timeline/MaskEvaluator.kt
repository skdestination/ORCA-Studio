package com.litecut.app.timeline

object MaskEvaluator {
    /**
     * Evaluates the active animated parameters of a Mask at the current relative offset.
     * Modifies the parameters in-place to ensure zero-allocation runtime execution during 120 FPS playback.
     */
    fun evaluate(
        clip: Clip,
        mask: Mask,
        relativeTimeOffset: Double
    ) {
        if (!mask.isEnabled) return

        // Helper to evaluate property with dynamic fallback support
        fun evaluateProperty(propName: String, fallback: Float): Float {
            val previousPropertyFallback = clip.additionalProperties[propName]
            clip.additionalProperties[propName] = fallback
            
            val evaluatedValue = AnimationEvaluator.evaluate(
                clip = clip,
                property = propName,
                timeOffset = relativeTimeOffset
            ).toFloat()

            if (previousPropertyFallback != null) {
                clip.additionalProperties[propName] = previousPropertyFallback
            } else {
                clip.additionalProperties.remove(propName)
            }
            return evaluatedValue
        }

        val id = mask.id
        
        // 1. Core properties
        mask.feather = evaluateProperty("mask_${id}_feather", mask.feather).coerceIn(0.0f, 500.0f)
        mask.opacity = evaluateProperty("mask_${id}_opacity", mask.opacity).coerceIn(0.0f, 1.0f)
        mask.expansion = evaluateProperty("mask_${id}_expansion", mask.expansion).coerceIn(-500.0f, 500.0f)

        // 2. Shape properties
        val shape = mask.shape
        shape.centerX = evaluateProperty("mask_${id}_centerX", shape.centerX)
        shape.centerY = evaluateProperty("mask_${id}_centerY", shape.centerY)
        shape.width = evaluateProperty("mask_${id}_width", shape.width).coerceAtLeast(0.0f)
        shape.height = evaluateProperty("mask_${id}_height", shape.height).coerceAtLeast(0.0f)
        shape.rotation = evaluateProperty("mask_${id}_rotation", shape.rotation)
        shape.roundness = evaluateProperty("mask_${id}_roundness", shape.roundness).coerceIn(0.0f, 1.0f)

        // 3. Control points (for custom paths / rotoscoping)
        val path = shape.path
        for (i in 0 until path.points.size) {
            val pt = path.points[i]
            pt.x = evaluateProperty("mask_${id}_pt${i}_x", pt.x)
            pt.y = evaluateProperty("mask_${id}_pt${i}_y", pt.y)
            pt.inTangentX = evaluateProperty("mask_${id}_pt${i}_inX", pt.inTangentX)
            pt.inTangentY = evaluateProperty("mask_${id}_pt${i}_inY", pt.inTangentY)
            pt.outTangentX = evaluateProperty("mask_${id}_pt${i}_outX", pt.outTangentX)
            pt.outTangentY = evaluateProperty("mask_${id}_pt${i}_outY", pt.outTangentY)
        }
    }

    /**
     * Evaluates an entire stack of masks sequentially.
     */
    fun evaluateStack(
        clip: Clip,
        stack: MaskStack,
        relativeTimeOffset: Double
    ) {
        for (mask in stack.masks) {
            evaluate(clip, mask, relativeTimeOffset)
        }
    }
}
