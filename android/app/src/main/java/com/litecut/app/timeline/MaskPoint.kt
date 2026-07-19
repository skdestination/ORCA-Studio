package com.litecut.app.timeline

import org.json.JSONObject

data class MaskPoint(
    var x: Float,
    var y: Float,
    var inTangentX: Float = 0.0f,
    var inTangentY: Float = 0.0f,
    var outTangentX: Float = 0.0f,
    var outTangentY: Float = 0.0f,
    var isCorner: Boolean = true
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("x", x.toDouble())
        json.put("y", y.toDouble())
        json.put("inTangentX", inTangentX.toDouble())
        json.put("inTangentY", inTangentY.toDouble())
        json.put("outTangentX", outTangentX.toDouble())
        json.put("outTangentY", outTangentY.toDouble())
        json.put("isCorner", isCorner)
        return json
    }

    fun copy(): MaskPoint {
        return MaskPoint(x, y, inTangentX, inTangentY, outTangentX, outTangentY, isCorner)
    }

    companion object {
        fun fromJSONObject(json: JSONObject): MaskPoint {
            return MaskPoint(
                x = json.getDouble("x").toFloat(),
                y = json.getDouble("y").toFloat(),
                inTangentX = json.optDouble("inTangentX", 0.0).toFloat(),
                inTangentY = json.optDouble("inTangentY", 0.0).toFloat(),
                outTangentX = json.optDouble("outTangentX", 0.0).toFloat(),
                outTangentY = json.optDouble("outTangentY", 0.0).toFloat(),
                isCorner = json.optBoolean("isCorner", true)
            )
        }
    }
}
