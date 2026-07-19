package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject

data class ColorStack(
    val id: String,
    var targetClipId: String,
    
    // Serial processing stack elements
    val preAdjustments: ColorAdjustment = ColorAdjustment(),
    val primaryLuts: MutableList<LUT> = mutableListOf(),
    val postAdjustments: ColorAdjustment = ColorAdjustment(),
    val secondaryLuts: MutableList<LUT> = mutableListOf(),
    
    var outputProfile: ColorProfile = ColorProfile.REC709_SDR
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("targetClipId", targetClipId)
        json.put("preAdjustments", preAdjustments.toJSONObject())
        
        val primArr = JSONArray()
        for (lut in primaryLuts) {
            primArr.put(lut.toJSONObject())
        }
        json.put("primaryLuts", primArr)
        
        json.put("postAdjustments", postAdjustments.toJSONObject())
        
        val secArr = JSONArray()
        for (lut in secondaryLuts) {
            secArr.put(lut.toJSONObject())
        }
        json.put("secondaryLuts", secArr)
        
        json.put("outputProfileId", outputProfile.id)
        return json
    }

    fun copyFrom(other: ColorStack) {
        this.targetClipId = other.targetClipId
        this.preAdjustments.copyFrom(other.preAdjustments)
        this.primaryLuts.clear()
        this.primaryLuts.addAll(other.primaryLuts)
        this.postAdjustments.copyFrom(other.postAdjustments)
        this.secondaryLuts.clear()
        this.secondaryLuts.addAll(other.secondaryLuts)
        this.outputProfile = other.outputProfile
    }

    companion object {
        fun fromJSONObject(json: JSONObject): ColorStack {
            val stack = ColorStack(
                id = json.getString("id"),
                targetClipId = json.getString("targetClipId")
            )
            
            val preObj = json.optJSONObject("preAdjustments")
            if (preObj != null) {
                stack.preAdjustments.copyFrom(ColorAdjustment.fromJSONObject(preObj))
            }
            
            val primArr = json.optJSONArray("primaryLuts")
            if (primArr != null) {
                for (i in 0 until primArr.length()) {
                    stack.primaryLuts.add(LUT.fromJSONObject(primArr.getJSONObject(i)))
                }
            }
            
            val postObj = json.optJSONObject("postAdjustments")
            if (postObj != null) {
                stack.postAdjustments.copyFrom(ColorAdjustment.fromJSONObject(postObj))
            }
            
            val secArr = json.optJSONArray("secondaryLuts")
            if (secArr != null) {
                for (i in 0 until secArr.length()) {
                    stack.secondaryLuts.add(LUT.fromJSONObject(secArr.getJSONObject(i)))
                }
            }
            
            val profId = json.optString("outputProfileId", "rec709_sdr")
            stack.outputProfile = when (profId) {
                "rec709_sdr" -> ColorProfile.REC709_SDR
                "srgb_sdr" -> ColorProfile.SRGB_SDR
                "rec2020_hdr10" -> ColorProfile.REC2020_HDR10
                "aces_filmic" -> ColorProfile.ACES_FILMIC
                else -> ColorProfile.REC709_SDR
            }
            
            return stack
        }
    }
}
