package com.litecut.app.timeline

import org.json.JSONObject

enum class TrackType {
    VIDEO, AUDIO, TEXT, VOICEOVER
}

data class TimelineTrack(
    val id: String,
    val type: TrackType,
    var order: Int,
    var isMuted: Boolean = false,
    var isHidden: Boolean = false,
    var isLocked: Boolean = false,
    var name: String? = null
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("type", type.name.lowercase())
        json.put("order", order)
        json.put("isMuted", isMuted)
        json.put("isHidden", isHidden)
        json.put("isLocked", isLocked)
        name?.let { json.put("name", it) }
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): TimelineTrack {
            val typeStr = json.optString("type", "VIDEO").uppercase()
            val type = try {
                TrackType.valueOf(typeStr)
            } catch (e: Exception) {
                TrackType.VIDEO
            }
            return TimelineTrack(
                id = json.getString("id"),
                type = type,
                order = json.optInt("order", 0),
                isMuted = json.optBoolean("isMuted", false),
                isHidden = json.optBoolean("isHidden", false),
                isLocked = json.optBoolean("isLocked", false),
                name = json.optString("name", null)
            )
        }
    }
}
