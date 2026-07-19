package com.litecut.app.timeline

import org.json.JSONObject

enum class MaskOperation {
    ADD,
    SUBTRACT,
    INTERSECT,
    DIFFERENCE
}

data class Mask(
    val id: String,
    val type: MaskType,
    var name: String,
    var isEnabled: Boolean = true,
    var isInverted: Boolean = false,
    var feather: Float = 0.0f, // 0.0 to 100.0 pixels/percent
    var opacity: Float = 1.0f, // 0.0 to 1.0
    var expansion: Float = 0.0f, // shrink or expand mask outline (-100 to 100)
    var operation: MaskOperation = MaskOperation.ADD,
    val shape: MaskShape = MaskShape()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("type", type.name)
        json.put("name", name)
        json.put("isEnabled", isEnabled)
        json.put("isInverted", isInverted)
        json.put("feather", feather.toDouble())
        json.put("opacity", opacity.toDouble())
        json.put("expansion", expansion.toDouble())
        json.put("operation", operation.name)
        json.put("shape", shape.toJSONObject())
        return json
    }

    fun copy(): Mask {
        return Mask(
            id = id,
            type = type,
            name = name,
            isEnabled = isEnabled,
            isInverted = isInverted,
            feather = feather,
            opacity = opacity,
            expansion = expansion,
            operation = operation,
            shape = shape.copy()
        )
    }

    companion object {
        fun fromJSONObject(json: JSONObject): Mask {
            val typeStr = json.getString("type")
            val opStr = json.optString("operation", MaskOperation.ADD.name)
            val mask = Mask(
                id = json.getString("id"),
                type = MaskType.valueOf(typeStr),
                name = json.getString("name"),
                isEnabled = json.optBoolean("isEnabled", true),
                isInverted = json.optBoolean("isInverted", false),
                feather = json.optDouble("feather", 0.0).toFloat(),
                opacity = json.optDouble("opacity", 1.0).toFloat(),
                expansion = json.optDouble("expansion", 0.0).toFloat(),
                operation = MaskOperation.valueOf(opStr),
                shape = MaskShape.fromJSONObject(json.getJSONObject("shape"))
            )
            return mask
        }
    }
}
