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

fun deepCloneValue(value: Any?): Any? {
    return when (value) {
        null -> null
        is String, is Number, is Boolean, is Char, is Enum<*> -> value
        is Map<*, *> -> {
            val copy = LinkedHashMap<String, Any?>()
            for ((k, v) in value) {
                if (k != null) {
                    copy[k.toString()] = deepCloneValue(v)
                }
            }
            copy
        }
        is List<*> -> {
            val copy = ArrayList<Any?>(value.size)
            for (item in value) {
                copy.add(deepCloneValue(item))
            }
            copy
        }
        is Set<*> -> {
            val copy = LinkedHashSet<Any?>(value.size)
            for (item in value) {
                copy.add(deepCloneValue(item))
            }
            copy
        }
        is Collection<*> -> {
            val copy = ArrayList<Any?>(value.size)
            for (item in value) {
                copy.add(deepCloneValue(item))
            }
            copy
        }
        is JSONObject -> {
            try {
                JSONObject(value.toString())
            } catch (e: Exception) {
                JSONObject()
            }
        }
        is JSONArray -> {
            try {
                JSONArray(value.toString())
            } catch (e: Exception) {
                JSONArray()
            }
        }
        else -> value
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
    var trimStartSeconds: Double = 0.0,
    var originalDurationSeconds: Double? = null,
    var speed: Double = 1.0,
    // Store all other React-only properties (keyframes, styling, etc.) dynamically
    // so they are fully preserved during operations.
    val additionalProperties: MutableMap<String, Any?> = mutableMapOf()
) {
    fun deepCopy(
        id: String = this.id,
        layerId: String = this.layerId,
        type: ClipType = this.type,
        src: String = this.src,
        name: String? = this.name,
        leftSeconds: Double = this.leftSeconds,
        durationSeconds: Double = this.durationSeconds,
        trimStartSeconds: Double = this.trimStartSeconds,
        originalDurationSeconds: Double? = this.originalDurationSeconds,
        speed: Double = this.speed,
        additionalProperties: MutableMap<String, Any?> = this.additionalProperties
    ): Clip {
        val clonedMap = LinkedHashMap<String, Any?>()
        for ((k, v) in additionalProperties) {
            clonedMap[k] = deepCloneValue(v)
        }
        return Clip(
            id = id,
            layerId = layerId,
            type = type,
            src = src,
            name = name,
            leftSeconds = leftSeconds,
            durationSeconds = durationSeconds,
            trimStartSeconds = trimStartSeconds,
            originalDurationSeconds = originalDurationSeconds,
            speed = speed,
            additionalProperties = clonedMap
        )
    }

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

var Clip.text: String?
    get() = additionalProperties["text"] as? String
    set(value) { additionalProperties["text"] = value }

var Clip.volume: Float
    get() = (additionalProperties["volume"] as? Number)?.toFloat() ?: 1.0f
    set(value) { additionalProperties["volume"] = value }

var Clip.brightness: Float
    get() = (additionalProperties["brightness"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["brightness"] = value }

var Clip.contrast: Float
    get() = (additionalProperties["contrast"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["contrast"] = value }

var Clip.saturation: Float
    get() = (additionalProperties["saturation"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["saturation"] = value }

var Clip.temperature: Float
    get() = (additionalProperties["temperature"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["temperature"] = value }

