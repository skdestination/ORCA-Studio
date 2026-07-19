package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

enum class ParagraphAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

data class TextStyle(
    var fontName: String = "sans-serif",
    var fontSize: Float = 36.0f,
    var fontWeight: String = "normal",
    var fontStyle: String = "normal",
    var letterSpacing: Float = 0.0f,
    var lineHeight: Float = 1.2f,
    var alignment: ParagraphAlignment = ParagraphAlignment.CENTER,
    var fillColor: Int = 0xFFFFFFFF.toInt(), // ARGB
    var isGradientFillEnabled: Boolean = false,
    val gradientColors: MutableList<Int> = mutableListOf(0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt()),
    var strokeColor: Int = 0xFF000000.toInt(),
    var strokeWidth: Float = 0.0f, // 0 means disabled
    var isShadowEnabled: Boolean = false,
    var shadowColor: Int = 0x80000000.toInt(),
    var shadowOffsetX: Float = 2.0f,
    var shadowOffsetY: Float = 2.0f,
    var shadowRadius: Float = 3.0f,
    var isBackgroundEnabled: Boolean = false,
    var backgroundColor: Int = 0x40000000.toInt(),
    var backgroundPadding: Float = 8.0f
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("fontName", fontName)
        json.put("fontSize", fontSize.toDouble())
        json.put("fontWeight", fontWeight)
        json.put("fontStyle", fontStyle)
        json.put("letterSpacing", letterSpacing.toDouble())
        json.put("lineHeight", lineHeight.toDouble())
        json.put("alignment", alignment.name)
        json.put("fillColor", fillColor)
        json.put("isGradientFillEnabled", isGradientFillEnabled)
        
        val gradArray = JSONArray()
        for (color in gradientColors) {
            gradArray.put(color)
        }
        json.put("gradientColors", gradArray)
        
        json.put("strokeColor", strokeColor)
        json.put("strokeWidth", strokeWidth.toDouble())
        json.put("isShadowEnabled", isShadowEnabled)
        json.put("shadowColor", shadowColor)
        json.put("shadowOffsetX", shadowOffsetX.toDouble())
        json.put("shadowOffsetY", shadowOffsetY.toDouble())
        json.put("shadowRadius", shadowRadius.toDouble())
        json.put("isBackgroundEnabled", isBackgroundEnabled)
        json.put("backgroundColor", backgroundColor)
        json.put("backgroundPadding", backgroundPadding.toDouble())
        return json
    }

    fun copy(): TextStyle {
        return TextStyle(
            fontName = fontName,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            alignment = alignment,
            fillColor = fillColor,
            isGradientFillEnabled = isGradientFillEnabled,
            gradientColors = gradientColors.toMutableList(),
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            isShadowEnabled = isShadowEnabled,
            shadowColor = shadowColor,
            shadowOffsetX = shadowOffsetX,
            shadowOffsetY = shadowOffsetY,
            shadowRadius = shadowRadius,
            isBackgroundEnabled = isBackgroundEnabled,
            backgroundColor = backgroundColor,
            backgroundPadding = backgroundPadding
        )
    }

    companion object {
        fun fromJSONObject(json: JSONObject): TextStyle {
            val style = TextStyle(
                fontName = json.optString("fontName", "sans-serif"),
                fontSize = json.optDouble("fontSize", 36.0).toFloat(),
                fontWeight = json.optString("fontWeight", "normal"),
                fontStyle = json.optString("fontStyle", "normal"),
                letterSpacing = json.optDouble("letterSpacing", 0.0).toFloat(),
                lineHeight = json.optDouble("lineHeight", 1.2).toFloat(),
                alignment = ParagraphAlignment.valueOf(json.optString("alignment", ParagraphAlignment.CENTER.name)),
                fillColor = json.optInt("fillColor", 0xFFFFFFFF.toInt()),
                isGradientFillEnabled = json.optBoolean("isGradientFillEnabled", false),
                strokeColor = json.optInt("strokeColor", 0xFF000000.toInt()),
                strokeWidth = json.optDouble("strokeWidth", 0.0).toFloat(),
                isShadowEnabled = json.optBoolean("isShadowEnabled", false),
                shadowColor = json.optInt("shadowColor", 0x80000000.toInt()),
                shadowOffsetX = json.optDouble("shadowOffsetX", 2.0).toFloat(),
                shadowOffsetY = json.optDouble("shadowOffsetY", 2.0).toFloat(),
                shadowRadius = json.optDouble("shadowRadius", 3.0).toFloat(),
                isBackgroundEnabled = json.optBoolean("isBackgroundEnabled", false),
                backgroundColor = json.optInt("backgroundColor", 0x40000000.toInt()),
                backgroundPadding = json.optDouble("backgroundPadding", 8.0).toFloat()
            )
            
            val gradArray = json.optJSONArray("gradientColors")
            if (gradArray != null) {
                style.gradientColors.clear()
                for (i in 0 until gradArray.length()) {
                    style.gradientColors.add(gradArray.getInt(i))
                }
            }
            return style
        }
    }
}
