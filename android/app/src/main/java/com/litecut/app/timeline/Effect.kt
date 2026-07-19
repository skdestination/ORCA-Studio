package com.litecut.app.timeline

import org.json.JSONObject

data class Effect(
    val id: String,
    val type: EffectType,
    var name: String,
    var isEnabled: Boolean = true,
    val parameters: MutableMap<String, EffectParameter> = mutableMapOf()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("type", type.name)
        json.put("name", name)
        json.put("isEnabled", isEnabled)
        
        val paramsJson = JSONObject()
        for ((key, value) in parameters) {
            paramsJson.put(key, value.toJSONObject())
        }
        json.put("parameters", paramsJson)
        return json
    }

    fun copy(): Effect {
        val copiedParams = mutableMapOf<String, EffectParameter>()
        for ((k, v) in parameters) {
            copiedParams[k] = v.copy()
        }
        return Effect(id, type, name, isEnabled, copiedParams)
    }

    companion object {
        fun fromJSONObject(json: JSONObject): Effect {
            val typeStr = json.getString("type")
            val effect = Effect(
                id = json.getString("id"),
                type = EffectType.valueOf(typeStr),
                name = json.getString("name"),
                isEnabled = json.optBoolean("isEnabled", true)
            )
            
            val paramsJson = json.optJSONObject("parameters")
            if (paramsJson != null) {
                val keys = paramsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    effect.parameters[key] = EffectParameter.fromJSONObject(paramsJson.getJSONObject(key))
                }
            }
            return effect
        }
    }
}
