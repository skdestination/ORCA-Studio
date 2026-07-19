package com.litecut.app.timeline

import org.json.JSONObject

data class EffectParameter(
    val name: String,
    var value: Float,
    val defaultValue: Float,
    val minValue: Float,
    val maxValue: Float
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("value", value.toDouble())
        json.put("defaultValue", defaultValue.toDouble())
        json.put("minValue", minValue.toDouble())
        json.put("maxValue", maxValue.toDouble())
        return json
    }

    fun copy(): EffectParameter {
        return EffectParameter(name, value, defaultValue, minValue, maxValue)
    }

    companion object {
        fun fromJSONObject(json: JSONObject): EffectParameter {
            return EffectParameter(
                name = json.getString("name"),
                value = json.getDouble("value").toFloat(),
                defaultValue = json.getDouble("defaultValue").toFloat(),
                minValue = json.getDouble("minValue").toFloat(),
                maxValue = json.getDouble("maxValue").toFloat()
            )
        }
    }
}
