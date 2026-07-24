package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

object CompositionResolver {
    /**
     * Resolves a Timeline Clip's properties at a given time offset into a target CompositionNode.
     * Implements zero-allocation property resolution during active playback.
     */
    fun resolve(
        clip: Clip,
        currentTime: Double,
        layerOrder: Int,
        isProxyMode: Boolean,
        targetNode: CompositionNode
    ) {
        val rawOffset = currentTime - clip.leftSeconds
        var relativeOffset = rawOffset * clip.speed

        val speedCurvePreset = clip.additionalProperties["speedCurve"] as? String
        if (speedCurvePreset != null && speedCurvePreset != "None") {
            val progress = if (clip.durationSeconds > 0) (rawOffset / clip.durationSeconds).coerceIn(0.0, 1.0) else 0.0
            val factor = when (speedCurvePreset) {
                "Hero" -> BezierCurve.evaluate(progress, 0.12, 0.0, 0.39, 0.0) * 3.0 + 0.5
                "Bullet Time" -> BezierCurve.evaluate(progress, 0.0, 1.0, 1.0, 0.0) * 0.2 + 0.1
                "Montage" -> BezierCurve.evaluate(progress, 0.25, 0.1, 0.25, 1.0) * 2.5
                "Flash" -> BezierCurve.evaluate(progress, 0.6, 0.04, 0.98, 0.335) * 4.0
                "Jump" -> BezierCurve.evaluate(progress, 0.4, 0.0, 0.2, 1.0) * 2.0
                else -> 1.0
            }
            relativeOffset *= factor
        }

        targetNode.id = "${clip.id}:$currentTime"
        targetNode.clipId = clip.id
        targetNode.layerId = clip.layerId
        targetNode.layerOrder = layerOrder
        targetNode.type = clip.type
        targetNode.src = clip.src
        targetNode.relativeTimeOffset = relativeOffset
        targetNode.isProxy = isProxyMode

        // Resolve animatable properties using AnimationEvaluator (linked with master clock)
        targetNode.opacity = AnimationEvaluator.evaluate(clip, "opacity", relativeOffset).toFloat()
        
        // Handle Scale X / Y separately or together (defaults to scale)
        val hasScaleX = clip.additionalProperties.containsKey("scale_x") || hasKeyframeProperty(clip, "scale_x")
        val hasScaleY = clip.additionalProperties.containsKey("scale_y") || hasKeyframeProperty(clip, "scale_y")
        
        if (hasScaleX || hasScaleY) {
            targetNode.scaleX = AnimationEvaluator.evaluate(clip, "scale_x", relativeOffset).toFloat()
            targetNode.scaleY = AnimationEvaluator.evaluate(clip, "scale_y", relativeOffset).toFloat()
        } else {
            val scaleVal = AnimationEvaluator.evaluate(clip, "scale", relativeOffset).toFloat()
            targetNode.scaleX = scaleVal
            targetNode.scaleY = scaleVal
        }

        // Translation
        targetNode.translationX = AnimationEvaluator.evaluate(clip, "translation_x", relativeOffset).toFloat()
        targetNode.translationY = AnimationEvaluator.evaluate(clip, "translation_y", relativeOffset).toFloat()

        // Rotation
        targetNode.rotation = AnimationEvaluator.evaluate(clip, "rotation", relativeOffset).toFloat()

        // Volume
        targetNode.volume = AnimationEvaluator.evaluate(clip, "volume", relativeOffset).toFloat()

        // Crop properties (can be static additionalProperties, or fallback to 0)
        targetNode.cropLeft = (clip.additionalProperties["crop_left"] as? Number)?.toFloat() ?: 0.0f
        targetNode.cropTop = (clip.additionalProperties["crop_top"] as? Number)?.toFloat() ?: 0.0f
        targetNode.cropRight = (clip.additionalProperties["crop_right"] as? Number)?.toFloat() ?: 0.0f
        targetNode.cropBottom = (clip.additionalProperties["crop_bottom"] as? Number)?.toFloat() ?: 0.0f

        // Future-ready properties
        targetNode.blendMode = (clip.additionalProperties["blend_mode"] as? String) ?: "NORMAL"
        targetNode.isAdjustmentLayer = (clip.additionalProperties["is_adjustment"] as? Boolean) ?: false
        targetNode.effectId = (clip.additionalProperties["effect_id"] as? String)
        targetNode.transitionType = (clip.additionalProperties["transition_type"] as? String)
        targetNode.transitionDuration = (clip.additionalProperties["transition_duration"] as? Number)?.toDouble() ?: 0.0
    }

    private fun hasKeyframeProperty(clip: Clip, property: String): Boolean {
        val kfsObj = clip.additionalProperties["keyframes"]
        if (kfsObj is JSONArray) {
            for (i in 0 until kfsObj.length()) {
                val kf = kfsObj.optJSONObject(i) ?: continue
                if (kf.optString("property").lowercase() == property.lowercase()) {
                    return true
                }
            }
        }
        val keyframesMap = clip.additionalProperties["keyframes_map"]
        if (keyframesMap is JSONObject) {
            return keyframesMap.has(property)
        }
        return false
    }
}
