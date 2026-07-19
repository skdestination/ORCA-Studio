package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

data class CharLayout(
    var charValue: Char = ' ',
    var x: Float = 0.0f,
    var y: Float = 0.0f,
    var width: Float = 0.0f,
    var height: Float = 0.0f,
    var lineIndex: Int = 0,
    var wordIndex: Int = 0
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("charValue", charValue.toString())
        json.put("x", x.toDouble())
        json.put("y", y.toDouble())
        json.put("width", width.toDouble())
        json.put("height", height.toDouble())
        json.put("lineIndex", lineIndex)
        json.put("wordIndex", wordIndex)
        return json
    }

    fun copy(): CharLayout {
        return CharLayout(charValue, x, y, width, height, lineIndex, wordIndex)
    }
}

data class LineLayout(
    var text: String = "",
    var x: Float = 0.0f,
    var y: Float = 0.0f,
    var width: Float = 0.0f,
    var height: Float = 0.0f,
    var startIndex: Int = 0,
    var endIndex: Int = 0
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("text", text)
        json.put("x", x.toDouble())
        json.put("y", y.toDouble())
        json.put("width", width.toDouble())
        json.put("height", height.toDouble())
        json.put("startIndex", startIndex)
        json.put("endIndex", endIndex)
        return json
    }

    fun copy(): LineLayout {
        return LineLayout(text, x, y, width, height, startIndex, endIndex)
    }
}

class TextLayout {
    var width: Float = 0.0f
    var height: Float = 0.0f
    val lines: MutableList<LineLayout> = mutableListOf()
    val characters: MutableList<CharLayout> = mutableListOf()

    fun clear() {
        width = 0.0f
        height = 0.0f
        lines.clear()
        characters.clear()
    }

    fun copyFrom(other: TextLayout) {
        this.width = other.width
        this.height = other.height
        this.lines.clear()
        for (line in other.lines) {
            this.lines.add(line.copy())
        }
        this.characters.clear()
        for (char in other.characters) {
            this.characters.add(char.copy())
        }
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("width", width.toDouble())
        json.put("height", height.toDouble())
        
        val linesArray = JSONArray()
        for (line in lines) {
            linesArray.put(line.toJSONObject())
        }
        json.put("lines", linesArray)

        val charsArray = JSONArray()
        for (char in characters) {
            charsArray.put(char.toJSONObject())
        }
        json.put("characters", charsArray)
        
        return json
    }
}
