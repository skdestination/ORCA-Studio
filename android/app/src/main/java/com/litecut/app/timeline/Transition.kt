package com.litecut.app.timeline

import org.json.JSONObject

data class Transition(
    val id: String,
    var type: TransitionType,
    var durationSeconds: Double,
    var centerTimeSeconds: Double,
    var outgoingClipId: String?,
    var incomingClipId: String?,
    var layerId: String,
    var easeType: InterpolationType = InterpolationType.LINEAR,
    val additionalParams: MutableMap<String, Any?> = mutableMapOf()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("type", type.name)
        json.put("durationSeconds", durationSeconds)
        json.put("centerTimeSeconds", centerTimeSeconds)
        outgoingClipId?.let { json.put("outgoingClipId", it) }
        incomingClipId?.let { json.put("incomingClipId", it) }
        json.put("layerId", layerId)
        json.put("easeType", easeType.name)
        
        val paramsJson = JSONObject()
        for ((key, value) in additionalParams) {
            paramsJson.put(key, value)
        }
        json.put("additionalParams", paramsJson)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): Transition {
            val id = json.getString("id")
            val type = TransitionType.valueOf(json.optString("type", TransitionType.CROSS_DISSOLVE.name))
            val durationSeconds = json.getDouble("durationSeconds")
            val centerTimeSeconds = json.getDouble("centerTimeSeconds")
            val outgoingClipId = if (json.has("outgoingClipId")) json.getString("outgoingClipId") else null
            val incomingClipId = if (json.has("incomingClipId")) json.getString("incomingClipId") else null
            val layerId = json.getString("layerId")
            val easeType = InterpolationType.valueOf(json.optString("easeType", InterpolationType.LINEAR.name))
            
            val additionalParams = mutableMapOf<String, Any?>()
            val paramsJson = json.optJSONObject("additionalParams")
            if (paramsJson != null) {
                val keys = paramsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    additionalParams[key] = paramsJson.get(key)
                }
            }
            
            return Transition(
                id = id,
                type = type,
                durationSeconds = durationSeconds,
                centerTimeSeconds = centerTimeSeconds,
                outgoingClipId = outgoingClipId,
                incomingClipId = incomingClipId,
                layerId = layerId,
                easeType = easeType,
                additionalParams = additionalParams
            )
        }
    }
}
