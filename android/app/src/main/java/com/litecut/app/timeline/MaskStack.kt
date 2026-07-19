package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

data class MaskStack(
    val id: String,
    var targetClipId: String,
    val masks: MutableList<Mask> = mutableListOf()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("targetClipId", targetClipId)
        
        val masksArray = JSONArray()
        for (mask in masks) {
            masksArray.put(mask.toJSONObject())
        }
        json.put("masks", masksArray)
        return json
    }

    fun copy(): MaskStack {
        val copiedMasks = masks.map { it.copy() }.toMutableList()
        return MaskStack(id, targetClipId, copiedMasks)
    }

    companion object {
        fun fromJSONObject(json: JSONObject): MaskStack {
            val stack = MaskStack(
                id = json.getString("id"),
                targetClipId = json.getString("targetClipId")
            )
            val masksArray = json.optJSONArray("masks")
            if (masksArray != null) {
                for (i in 0 until masksArray.length()) {
                    stack.masks.add(Mask.fromJSONObject(masksArray.getJSONObject(i)))
                }
            }
            return stack
        }
    }
}
