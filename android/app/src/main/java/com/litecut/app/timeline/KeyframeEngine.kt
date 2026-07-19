package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

object KeyframeEngine {
    
    // --- Legacy Bridge Methods (Preserved for compatibility) ---

    fun splitKeyframes(keyframes: JSONArray, firstDuration: Double): Pair<JSONArray, JSONArray> {
        val left = JSONArray()
        val right = JSONArray()
        
        for (i in 0 until keyframes.length()) {
            val kf = keyframes.optJSONObject(i) ?: continue
            val timeOffset = kf.optDouble("timeOffset", 0.0)
            
            if (timeOffset < firstDuration) {
                left.put(kf)
            } else {
                val newKf = JSONObject(kf.toString())
                newKf.put("timeOffset", timeOffset - firstDuration)
                right.put(newKf)
            }
        }
        return Pair(left, right)
    }

    fun trimKeyframes(keyframes: JSONArray, change: Double): JSONArray {
        val result = JSONArray()
        for (i in 0 until keyframes.length()) {
            val kf = keyframes.optJSONObject(i) ?: continue
            val timeOffset = kf.optDouble("timeOffset", 0.0)
            
            if (timeOffset >= change) {
                val newKf = JSONObject(kf.toString())
                newKf.put("timeOffset", timeOffset - change)
                result.put(newKf)
            }
        }
        return result
    }

    // --- Production Keyframe Engine Operations ---

    /**
     * Parses the clip's additional properties JSON keyframes list.
     */
    private fun parseKeyframesFromClip(clip: Clip): ArrayList<Keyframe> {
        val list = ArrayList<Keyframe>()
        val kfsObj = clip.additionalProperties["keyframes"]
        if (kfsObj is JSONArray) {
            val len = kfsObj.length()
            for (i in 0 until len) {
                val json = kfsObj.optJSONObject(i) ?: continue
                try {
                    list.add(Keyframe.fromJSONObject(json))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return list
    }

    /**
     * Serializes a list of keyframes back into the clip's additional properties.
     */
    private fun saveKeyframesToClip(clip: Clip, list: List<Keyframe>) {
        val sorted = list.sortedBy { it.timeOffset }
        val array = JSONArray()
        for (kf in sorted) {
            array.put(kf.toJSONObject())
        }
        clip.additionalProperties["keyframes"] = array
    }

    /**
     * Finds a keyframe inside a Clip by its ID.
     */
    fun findKeyframeById(clip: Clip, id: String): Keyframe? {
        val list = parseKeyframesFromClip(clip)
        return list.find { it.id == id }
    }

    /**
     * Core internal add operation. Thread-safe, sorted, and invalidates cache.
     */
    @Synchronized
    fun addKeyframeInternal(clip: Clip, kf: Keyframe) {
        val list = parseKeyframesFromClip(clip)
        
        // Ensure no duplicate keyframe exists at the exact same offset for this property
        list.removeAll { it.timeOffset == kf.timeOffset && it.property.lowercase() == kf.property.lowercase() }
        list.removeAll { it.id == kf.id }
        
        list.add(kf)
        saveKeyframesToClip(clip, list)
        
        // Invalidate Keyframe Cache
        KeyframeCache.getInstance().invalidateProperty(clip.id, kf.property)
    }

    /**
     * Core internal delete operation. Thread-safe, sorted, and invalidates cache.
     */
    @Synchronized
    fun removeKeyframeInternal(clip: Clip, keyframeId: String) {
        val list = parseKeyframesFromClip(clip)
        val kf = list.find { it.id == keyframeId } ?: return
        
        list.removeAll { it.id == keyframeId }
        saveKeyframesToClip(clip, list)
        
        // Invalidate Keyframe Cache
        KeyframeCache.getInstance().invalidateProperty(clip.id, kf.property)
    }

    /**
     * Core internal update operation. Thread-safe, sorted, and invalidates cache.
     */
    @Synchronized
    fun updateKeyframeInternal(
        clip: Clip,
        keyframeId: String,
        newValue: Double? = null,
        newInterpolation: InterpolationType? = null,
        newTimeOffset: Double? = null
    ) {
        val list = parseKeyframesFromClip(clip)
        val index = list.indexOfFirst { it.id == keyframeId }
        if (index == -1) return

        val original = list[index]
        val updated = Keyframe(
            id = original.id,
            property = original.property,
            timeOffset = newTimeOffset ?: original.timeOffset,
            value = newValue ?: original.value,
            interpolation = newInterpolation ?: original.interpolation,
            cp1X = original.cp1X,
            cp1Y = original.cp1Y,
            cp2X = original.cp2X,
            cp2Y = original.cp2Y
        )

        list[index] = updated
        saveKeyframesToClip(clip, list)
        
        // Invalidate Keyframe Cache
        KeyframeCache.getInstance().invalidateProperty(clip.id, original.property)
    }
}
