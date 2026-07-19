package com.litecut.app.timeline

import kotlin.math.max

object AnimationEvaluator {
    /**
     * Evaluates the value of an animatable property for a Clip at a given relative time offset.
     * Uses O(log n) track lookup, interpolates, and stores in KeyframeCache.
     */
    fun evaluate(clip: Clip, property: String, timeOffset: Double): Double {
        val cache = KeyframeCache.getInstance()
        
        // Check cache first
        val cachedValue = cache.get(clip.id, property, timeOffset)
        if (cachedValue != null) {
            return cachedValue
        }

        // Build keyframes list for this property
        val kfList = ArrayList<Keyframe>()
        val kfsObj = clip.additionalProperties["keyframes"]
        
        if (kfsObj is org.json.JSONArray) {
            val len = kfsObj.length()
            for (i in 0 until len) {
                val kfJson = kfsObj.optJSONObject(i) ?: continue
                val prop = kfJson.optString("property")
                if (prop.lowercase() == property.lowercase()) {
                    kfList.add(Keyframe.fromJSONObject(kfJson))
                }
            }
        }

        // Also check "keyframes_map" just in case
        val keyframesMap = clip.additionalProperties["keyframes_map"]
        if (keyframesMap is org.json.JSONObject) {
            val arr = keyframesMap.optJSONArray(property)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    kfList.add(Keyframe.fromJSONObject(arr.getJSONObject(i)))
                }
            }
        }

        // Sort keyframes in chronological order
        kfList.sortBy { it.timeOffset }

        // If no keyframes are found, fallback to static clip properties
        if (kfList.isEmpty()) {
            val fallback = getStaticFallbackValue(clip, property)
            cache.put(clip.id, property, timeOffset, fallback)
            return fallback
        }

        // Evaluate using our binary search surrounding lookup
        val evaluated = when {
            kfList.size == 1 -> kfList[0].value
            timeOffset <= kfList.first().timeOffset -> kfList.first().value
            timeOffset >= kfList.last().timeOffset -> kfList.last().value
            else -> {
                // Perform binary search to find surrounding keyframes
                var low = 0
                var high = kfList.size - 1
                var foundIndex = -1
                
                while (low <= high) {
                    val mid = (low + high) ushr 1
                    val midTime = kfList[mid].timeOffset
                    
                    if (midTime == timeOffset) {
                        foundIndex = mid
                        break
                    } else if (midTime < timeOffset) {
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                
                if (foundIndex != -1) {
                    kfList[foundIndex].value
                } else {
                    // 'high' is the keyframe just before timeOffset, 'low' is the keyframe just after
                    val kf1 = kfList[high]
                    val kf2 = kfList[low]
                    KeyframeInterpolator.interpolate(timeOffset, kf1, kf2)
                }
            }
        }

        cache.put(clip.id, property, timeOffset, evaluated)
        return evaluated
    }

    private fun getStaticFallbackValue(clip: Clip, property: String): Double {
        val channel = KeyframeChannel.fromPropertyName(property)
        if (channel != null) {
            return when (channel) {
                KeyframeChannel.OPACITY -> clip.opacity.toDouble()
                KeyframeChannel.SCALE_X, KeyframeChannel.SCALE_Y -> clip.scale.toDouble()
                KeyframeChannel.ROTATION -> clip.rotation.toDouble()
                KeyframeChannel.SPEED -> clip.speed
                KeyframeChannel.VOLUME -> {
                    val vol = clip.additionalProperties["volume"]
                    if (vol is Number) vol.toDouble() else 1.0
                }
                else -> channel.defaultValue
            }
        }
        
        // Dynamic fallback from clip properties directly
        val value = clip.additionalProperties[property]
        if (value is Number) {
            return value.toDouble()
        }
        return 0.0
    }
}
