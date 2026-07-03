package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

object KeyframeEngine {
    
    fun splitKeyframes(keyframes: JSONArray, firstDuration: Double): Pair<JSONArray, JSONArray> {
        val left = JSONArray()
        val right = JSONArray()
        
        for (i in 0 until keyframes.length()) {
            val kf = keyframes.getJSONObject(i)
            val timeOffset = kf.getDouble("timeOffset")
            
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
            val kf = keyframes.getJSONObject(i)
            val timeOffset = kf.getDouble("timeOffset")
            
            if (timeOffset >= change) {
                val newKf = JSONObject(kf.toString())
                newKf.put("timeOffset", timeOffset - change)
                result.put(newKf)
            }
        }
        return result
    }
}
