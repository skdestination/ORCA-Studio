package com.litecut.app.timeline

import org.json.JSONObject

data class Keyframe(
    val id: String,
    val timeOffset: Double,
    val value: Double,
    val property: String, // e.g. "opacity", "volume", "scale"
    val interpolation: InterpolationType = InterpolationType.LINEAR,
    val cp1X: Double? = null,
    val cp1Y: Double? = null,
    val cp2X: Double? = null,
    val cp2Y: Double? = null
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("timeOffset", timeOffset)
        json.put("value", value)
        json.put("property", property)
        json.put("interpolation", interpolation.name.lowercase())
        cp1X?.let { json.put("cp1X", it) }
        cp1Y?.let { json.put("cp1Y", it) }
        cp2X?.let { json.put("cp2X", it) }
        cp2Y?.let { json.put("cp2Y", it) }
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): Keyframe {
            val interpStr = json.optString("interpolation", "linear").uppercase()
            val interp = try {
                InterpolationType.valueOf(interpStr)
            } catch (e: Exception) {
                InterpolationType.LINEAR
            }
            return Keyframe(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                timeOffset = json.getDouble("timeOffset"),
                value = json.getDouble("value"),
                property = json.getString("property"),
                interpolation = interp,
                cp1X = if (json.has("cp1X") && !json.isNull("cp1X")) json.getDouble("cp1X") else null,
                cp1Y = if (json.has("cp1Y") && !json.isNull("cp1Y")) json.getDouble("cp1Y") else null,
                cp2X = if (json.has("cp2X") && !json.isNull("cp2X")) json.getDouble("cp2X") else null,
                cp2Y = if (json.has("cp2Y") && !json.isNull("cp2Y")) json.getDouble("cp2Y") else null
            )
        }
    }
}
