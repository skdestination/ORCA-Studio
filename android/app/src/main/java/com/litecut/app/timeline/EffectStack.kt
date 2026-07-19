package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

data class EffectStack(
    val id: String,
    var targetClipId: String,
    val effects: MutableList<Effect> = mutableListOf()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("targetClipId", targetClipId)
        
        val effectsArray = JSONArray()
        for (effect in effects) {
            effectsArray.put(effect.toJSONObject())
        }
        json.put("effects", effectsArray)
        return json
    }

    fun copy(): EffectStack {
        val copiedEffects = effects.map { it.copy() }.toMutableList()
        return EffectStack(id, targetClipId, copiedEffects)
    }

    companion object {
        fun fromJSONObject(json: JSONObject): EffectStack {
            val stack = EffectStack(
                id = json.getString("id"),
                targetClipId = json.getString("targetClipId")
            )
            val effectsArray = json.optJSONArray("effects")
            if (effectsArray != null) {
                for (i in 0 until effectsArray.length()) {
                    stack.effects.add(Effect.fromJSONObject(effectsArray.getJSONObject(i)))
                }
            }
            return stack
        }
    }
}
