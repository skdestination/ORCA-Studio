package com.litecut.app.timeline

import org.json.JSONObject

enum class ExportState {
    IDLE,
    QUEUED,
    EXPORTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ExportSession(
    val id: String,
    val settings: ExportSettings,
    val outputPath: String,
    var state: ExportState = ExportState.IDLE,
    var startTimeMs: Long = 0L,
    var endTimeMs: Long = 0L,
    var errorMsg: String? = null,
    val stats: ExportStatistics = ExportStatistics()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("settings", settings.toJSONObject())
        json.put("outputPath", outputPath)
        json.put("state", state.name)
        json.put("startTimeMs", startTimeMs)
        json.put("endTimeMs", endTimeMs)
        json.put("errorMsg", errorMsg ?: "")
        json.put("stats", stats.toJSONObject())
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): ExportSession {
            val session = ExportSession(
                id = json.getString("id"),
                settings = ExportSettings.fromJSONObject(json.getJSONObject("settings")),
                outputPath = json.getString("outputPath"),
                state = ExportState.valueOf(json.optString("state", ExportState.IDLE.name)),
                startTimeMs = json.optLong("startTimeMs", 0L),
                endTimeMs = json.optLong("endTimeMs", 0L),
                errorMsg = json.optString("errorMsg", "").takeIf { it.isNotEmpty() }
            )
            return session
        }
    }
}
