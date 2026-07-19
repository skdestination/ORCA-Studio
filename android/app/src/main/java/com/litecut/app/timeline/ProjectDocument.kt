package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

data class ProjectDocument(
    val metadata: ProjectMetadata = ProjectMetadata(),
    val assets: MutableList<AssetReference> = mutableListOf(),
    var timelineData: JSONObject = JSONObject(),
    var playbackSettings: JSONObject = JSONObject(),
    var exportSettings: JSONObject = JSONObject()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("metadata", metadata.toJSONObject())
        
        val assetsArray = JSONArray()
        for (asset in assets) {
            assetsArray.put(asset.toJSONObject())
        }
        json.put("assets", assetsArray)
        
        json.put("timelineData", timelineData)
        json.put("playbackSettings", playbackSettings)
        json.put("exportSettings", exportSettings)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): ProjectDocument {
            val doc = ProjectDocument(
                metadata = ProjectMetadata.fromJSONObject(json.getJSONObject("metadata")),
                timelineData = json.optJSONObject("timelineData") ?: JSONObject(),
                playbackSettings = json.optJSONObject("playbackSettings") ?: JSONObject(),
                exportSettings = json.optJSONObject("exportSettings") ?: JSONObject()
            )
            
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    doc.assets.add(AssetReference.fromJSONObject(assetsArray.getJSONObject(i)))
                }
            }
            
            return doc
        }
    }
}
