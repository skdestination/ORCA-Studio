package com.litecut.app.timeline

import org.json.JSONObject
import java.util.UUID

data class ProjectMetadata(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Untitled Project",
    var createdAtMs: Long = System.currentTimeMillis(),
    var modifiedAtMs: Long = System.currentTimeMillis(),
    var creatorAppVersionCode: Int = 1,
    var creatorAppVersionName: String = "1.0.0",
    var durationMs: Long = 0L,
    var frameRate: Int = 30,
    var width: Int = 1920,
    var height: Int = 1080,
    var thumbnailPath: String = ""
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("createdAtMs", createdAtMs)
        json.put("modifiedAtMs", modifiedAtMs)
        json.put("creatorAppVersionCode", creatorAppVersionCode)
        json.put("creatorAppVersionName", creatorAppVersionName)
        json.put("durationMs", durationMs)
        json.put("frameRate", frameRate)
        json.put("width", width)
        json.put("height", height)
        json.put("thumbnailPath", thumbnailPath)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): ProjectMetadata {
            return ProjectMetadata(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Untitled Project"),
                createdAtMs = json.optLong("createdAtMs", System.currentTimeMillis()),
                modifiedAtMs = json.optLong("modifiedAtMs", System.currentTimeMillis()),
                creatorAppVersionCode = json.optInt("creatorAppVersionCode", 1),
                creatorAppVersionName = json.optString("creatorAppVersionName", "1.0.0"),
                durationMs = json.optLong("durationMs", 0L),
                frameRate = json.optInt("frameRate", 30),
                width = json.optInt("width", 1920),
                height = json.optInt("height", 1080),
                thumbnailPath = json.optString("thumbnailPath", "")
            )
        }
    }
}
