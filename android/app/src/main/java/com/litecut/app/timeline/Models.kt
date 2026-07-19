package com.litecut.app.timeline

import org.json.JSONObject
import org.json.JSONArray

enum class ClipType {
    VIDEO, IMAGE, AUDIO, TEXT
}

data class Layer(
    val id: String,
    var order: Int,
    var isMuted: Boolean,
    var isHidden: Boolean,
    var isLocked: Boolean = false,
    var name: String? = null
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("order", order)
        json.put("isMuted", isMuted)
        json.put("isHidden", isHidden)
        json.put("isLocked", isLocked)
        name?.let { json.put("name", it) }
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): Layer {
            return Layer(
                id = json.getString("id"),
                order = json.optInt("order", 0),
                isMuted = json.optBoolean("isMuted", false),
                isHidden = json.optBoolean("isHidden", false),
                isLocked = json.optBoolean("isLocked", false),
                name = json.optString("name", null)
            )
        }
    }
}

data class Clip(
    val id: String,
    var layerId: String,
    val type: ClipType,
    var src: String,
    var name: String? = null,
    var leftSeconds: Double,
    var durationSeconds: Double,
    var trimStartSeconds: Double,
    var originalDurationSeconds: Double? = null,
    var speed: Double = 1.0,
    // Store all other React-only properties (keyframes, styling, etc.) dynamically
    // so they are fully preserved during operations.
    val additionalProperties: MutableMap<String, Any?> = mutableMapOf()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("layerId", layerId)
        json.put("type", type.name.lowercase())
        json.put("src", src)
        name?.let { json.put("name", it) }
        json.put("leftSeconds", leftSeconds)
        json.put("durationSeconds", durationSeconds)
        json.put("trimStartSeconds", trimStartSeconds)
        originalDurationSeconds?.let { json.put("originalDurationSeconds", it) }
        json.put("speed", speed)

        // Put all preserved extra properties
        for ((key, value) in additionalProperties) {
            json.put(key, value)
        }
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): Clip {
            val id = json.getString("id")
            val layerId = json.getString("layerId")
            val typeStr = json.getString("type").uppercase()
            val type = try {
                ClipType.valueOf(typeStr)
            } catch (e: Exception) {
                ClipType.VIDEO
            }
            val src = json.getString("src")
            val name = json.optString("name", null)
            val leftSeconds = json.getDouble("leftSeconds")
            val durationSeconds = json.getDouble("durationSeconds")
            val trimStartSeconds = json.optDouble("trimStartSeconds", 0.0)
            val originalDurationSeconds = if (json.has("originalDurationSeconds") && !json.isNull("originalDurationSeconds")) {
                json.getDouble("originalDurationSeconds")
            } else null
            val speed = json.optDouble("speed", 1.0)

            val clip = Clip(
                id = id,
                layerId = layerId,
                type = type,
                src = src,
                name = name,
                leftSeconds = leftSeconds,
                durationSeconds = durationSeconds,
                trimStartSeconds = trimStartSeconds,
                originalDurationSeconds = originalDurationSeconds,
                speed = speed
            )

            // Extract additional fields to preserve them
            val keys = json.keys()
            val standardKeys = setOf(
                "id", "layerId", "type", "src", "name", "leftSeconds", 
                "durationSeconds", "trimStartSeconds", "originalDurationSeconds", "speed"
            )
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

// --- Clip Extension Properties for Animation & Composition Engine ---
val Clip.opacity: Float
    get() = (additionalProperties["opacity"] as? Number)?.toFloat() ?: 1.0f

val Clip.scale: Float
    get() = (additionalProperties["scale"] as? Number)?.toFloat() ?: 1.0f

val Clip.rotation: Float
    get() = (additionalProperties["rotation"] as? Number)?.toFloat() ?: 0.0f

val Clip.mute: Boolean
    get() = (additionalProperties["mute"] as? Boolean) ?: ((additionalProperties["mute"] as? Number)?.toDouble() ?: 0.0 > 0.5)

