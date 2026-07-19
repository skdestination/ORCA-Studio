package com.litecut.app.timeline

import org.json.JSONObject

data class TextLayer(
    val id: String,
    var name: String,
    var isEnabled: Boolean = true,
    val document: TextDocument = TextDocument(),
    val cachedLayout: TextLayout = TextLayout()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("isEnabled", isEnabled)
        json.put("document", document.toJSONObject())
        json.put("cachedLayout", cachedLayout.toJSONObject())
        return json
    }

    fun copy(): TextLayer {
        val copiedLayer = TextLayer(
            id = id,
            name = name,
            isEnabled = isEnabled,
            document = document.copy()
        )
        copiedLayer.cachedLayout.copyFrom(cachedLayout)
        return copiedLayer
    }

    companion object {
        fun fromJSONObject(json: JSONObject): TextLayer {
            val layer = TextLayer(
                id = json.getString("id"),
                name = json.getString("name"),
                isEnabled = json.optBoolean("isEnabled", true),
                document = TextDocument.fromJSONObject(json.getJSONObject("document"))
            )
            val layoutObj = json.optJSONObject("cachedLayout")
            if (layoutObj != null) {
                // Initialize layout size parameters from JSON
                layer.cachedLayout.width = layoutObj.optDouble("width", 0.0).toFloat()
                layer.cachedLayout.height = layoutObj.optDouble("height", 0.0).toFloat()
            }
            return layer
        }
    }
}
