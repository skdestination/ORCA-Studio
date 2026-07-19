package com.litecut.app.timeline

import org.json.JSONObject
import org.json.JSONArray


enum class MediaType {
    VIDEO, AUDIO, TEXT, IMAGE
}

data class TimelineClip(
    val id: String,
    var trackId: String,
    val mediaId: String,
    val mediaType: MediaType,
    var startTime: Double, // leftSeconds
    var duration: Double, // durationSeconds
    var trimIn: Double, // trimStartSeconds
    var trimOut: Double, // trimEndSeconds / originalDuration or calculated
    var speed: Double = 1.0,
    var rotation: Float = 0f,
    var scale: Float = 1f,
    var opacity: Float = 1f,
    var mute: Boolean = false,
    var hidden: Boolean = false,
    val effects: MutableList<String> = mutableListOf(),
    val keyframes: MutableMap<String, MutableList<Keyframe>> = mutableMapOf(),
    val additionalProperties: MutableMap<String, Any?> = mutableMapOf()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("layerId", trackId) // mapping for bridge compatibility
        json.put("type", mediaType.name.lowercase())
        json.put("src", mediaId) // mapping for bridge compatibility
        json.put("leftSeconds", startTime)
        json.put("durationSeconds", duration)
        json.put("trimStartSeconds", trimIn)
        json.put("trimOut", trimOut)
        json.put("speed", speed)
        json.put("rotation", rotation.toDouble())
        json.put("scale", scale.toDouble())
        json.put("opacity", opacity.toDouble())
        json.put("mute", mute)
        json.put("hidden", hidden)
        
        val effArr = JSONArray()
        effects.forEach { effArr.put(it) }
        json.put("effects", effArr)

        val kfObj = JSONObject()
        keyframes.forEach { (prop, list) ->
            val arr = JSONArray()
            list.forEach { arr.put(it.toJSONObject()) }
            kfObj.put(prop, arr)
        }
        json.put("keyframes_map", kfObj)

        additionalProperties.forEach { (k, v) ->
            json.put(k, v)
        }
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): TimelineClip {
            val id = json.getString("id")
            val trackId = json.optString("layerId", json.optString("trackId", ""))
            val typeStr = json.optString("type", json.optString("mediaType", "VIDEO")).uppercase()
            val mediaType = try {
                MediaType.valueOf(typeStr)
            } catch (e: Exception) {
                MediaType.VIDEO
            }
            val mediaId = json.optString("src", json.optString("mediaId", ""))
            val startTime = json.optDouble("leftSeconds", json.optDouble("startTime", 0.0))
            val duration = json.optDouble("durationSeconds", json.optDouble("duration", 10.0))
            val trimIn = json.optDouble("trimStartSeconds", json.optDouble("trimIn", 0.0))
            val trimOut = json.optDouble("trimOut", json.optDouble("originalDurationSeconds", 0.0))
            val speed = json.optDouble("speed", 1.0)
            val rotation = json.optDouble("rotation", 0.0).toFloat()
            val scale = json.optDouble("scale", 1.0).toFloat()
            val opacity = json.optDouble("opacity", 1.0).toFloat()
            val mute = json.optBoolean("mute", json.optBoolean("isMuted", false))
            val hidden = json.optBoolean("hidden", json.optBoolean("isHidden", false))

            val effectsList = mutableListOf<String>()
            if (json.has("effects")) {
                val arr = json.getJSONArray("effects")
                for (i in 0 until arr.length()) {
                    effectsList.add(arr.getString(i))
                }
            }

            val keyframesMap = mutableMapOf<String, MutableList<Keyframe>>()
            if (json.has("keyframes_map")) {
                val mapObj = json.getJSONObject("keyframes_map")
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = mapObj.getJSONArray(key)
                    val kfList = mutableListOf<Keyframe>()
                    for (i in 0 until arr.length()) {
                        kfList.add(Keyframe.fromJSONObject(arr.getJSONObject(i)))
                    }
                    keyframesMap[key] = kfList
                }
            }

            val clip = TimelineClip(
                id = id,
                trackId = trackId,
                mediaId = mediaId,
                mediaType = mediaType,
                startTime = startTime,
                duration = duration,
                trimIn = trimIn,
                trimOut = trimOut,
                speed = speed,
                rotation = rotation,
                scale = scale,
                opacity = opacity,
                mute = mute,
                hidden = hidden,
                effects = effectsList,
                keyframes = keyframesMap
            )

            // Dynamic key extraction to preserve any other dynamic metadata
            val standardKeys = setOf(
                "id", "layerId", "trackId", "type", "mediaType", "src", "mediaId",
                "leftSeconds", "startTime", "durationSeconds", "duration",
                "trimStartSeconds", "trimIn", "trimOut", "speed", "rotation",
                "scale", "opacity", "mute", "hidden", "effects", "keyframes_map"
            )
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in standardKeys) {
                    clip.additionalProperties[key] = json.get(key)
                }
            }
            return clip
        }
    }
}
