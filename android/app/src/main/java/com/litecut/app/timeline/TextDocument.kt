package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

data class StyledRange(
    val startIndex: Int,
    val endIndex: Int,
    val style: TextStyle
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("startIndex", startIndex)
        json.put("endIndex", endIndex)
        json.put("style", style.toJSONObject())
        return json
    }

    fun copy(): StyledRange {
        return StyledRange(startIndex, endIndex, style.copy())
    }

    companion object {
        fun fromJSONObject(json: JSONObject): StyledRange {
            return StyledRange(
                startIndex = json.getInt("startIndex"),
                endIndex = json.getInt("endIndex"),
                style = TextStyle.fromJSONObject(json.getJSONObject("style"))
            )
        }
    }
}

data class TextDocument(
    var text: String = "",
    val rootStyle: TextStyle = TextStyle(),
    val styledRanges: MutableList<StyledRange> = mutableListOf(),
    // Base coordinates for the text block center anchor
    var anchorX: Float = 0.5f,
    var anchorY: Float = 0.5f,
    var translationX: Float = 0.0f,
    var translationY: Float = 0.0f,
    var scaleX: Float = 1.0f,
    var scaleY: Float = 1.0f,
    var rotation: Float = 0.0f,
    var opacity: Float = 1.0f
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("text", text)
        json.put("rootStyle", rootStyle.toJSONObject())
        
        val rangesArray = JSONArray()
        for (range in styledRanges) {
            rangesArray.put(range.toJSONObject())
        }
        json.put("styledRanges", rangesArray)
        
        json.put("anchorX", anchorX.toDouble())
        json.put("anchorY", anchorY.toDouble())
        json.put("translationX", translationX.toDouble())
        json.put("translationY", translationY.toDouble())
        json.put("scaleX", scaleX.toDouble())
        json.put("scaleY", scaleY.toDouble())
        json.put("rotation", rotation.toDouble())
        json.put("opacity", opacity.toDouble())
        return json
    }

    fun copy(): TextDocument {
        val copiedRanges = styledRanges.map { it.copy() }.toMutableList()
        return TextDocument(
            text = text,
            rootStyle = rootStyle.copy(),
            styledRanges = copiedRanges,
            anchorX = anchorX,
            anchorY = anchorY,
            translationX = translationX,
            translationY = translationY,
            scaleX = scaleX,
            scaleY = scaleY,
            rotation = rotation,
            opacity = opacity
        )
    }

    companion object {
        fun fromJSONObject(json: JSONObject): TextDocument {
            val doc = TextDocument(
                text = json.optString("text", ""),
                rootStyle = TextStyle.fromJSONObject(json.getJSONObject("rootStyle")),
                anchorX = json.optDouble("anchorX", 0.5).toFloat(),
                anchorY = json.optDouble("anchorY", 0.5).toFloat(),
                translationX = json.optDouble("translationX", 0.0).toFloat(),
                translationY = json.optDouble("translationY", 0.0).toFloat(),
                scaleX = json.optDouble("scaleX", 1.0).toFloat(),
                scaleY = json.optDouble("scaleY", 1.0).toFloat(),
                rotation = json.optDouble("rotation", 0.0).toFloat(),
                opacity = json.optDouble("opacity", 1.0).toFloat()
            )
            
            val rangesArray = json.optJSONArray("styledRanges")
            if (rangesArray != null) {
                for (i in 0 until rangesArray.length()) {
                    doc.styledRanges.add(StyledRange.fromJSONObject(rangesArray.getJSONObject(i)))
                }
            }
            return doc
        }
    }
}
