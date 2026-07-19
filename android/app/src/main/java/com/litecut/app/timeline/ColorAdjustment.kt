package com.litecut.app.timeline

import org.json.JSONObject

data class ColorAdjustment(
    var exposure: Float = 0.0f,       // -4.0 to +4.0 eV
    var brightness: Float = 0.0f,     // -1.0 to +1.0
    var contrast: Float = 1.0f,       // 0.5 to 2.0
    var saturation: Float = 1.0f,     // 0.0 to 2.0
    var vibrance: Float = 1.0f,       // 0.0 to 2.0
    var temperature: Float = 0.0f,    // -1.0 (Cool) to +1.0 (Warm)
    var tint: Float = 0.0f,           // -1.0 (Green) to +1.0 (Magenta)
    var gamma: Float = 1.0f,          // 0.5 to 2.0
    var highlights: Float = 0.0f,     // -1.0 to +1.0
    var shadows: Float = 0.0f,        // -1.0 to +1.0
    var whites: Float = 0.0f,         // -1.0 to +1.0
    var blacks: Float = 0.0f,         // -1.0 to +1.0
    var fade: Float = 0.0f,           // 0.0 to 1.0
    var sharpenAmount: Float = 0.0f,  // 0.0 to 1.0
    
    // Future-proof properties for HDR, Dolby Vision, Log, and ACES workflows
    val customParameters: MutableMap<String, Float> = mutableMapOf()
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("exposure", exposure.toDouble())
        json.put("brightness", brightness.toDouble())
        json.put("contrast", contrast.toDouble())
        json.put("saturation", saturation.toDouble())
        json.put("vibrance", vibrance.toDouble())
        json.put("temperature", temperature.toDouble())
        json.put("tint", tint.toDouble())
        json.put("gamma", gamma.toDouble())
        json.put("highlights", highlights.toDouble())
        json.put("shadows", shadows.toDouble())
        json.put("whites", whites.toDouble())
        json.put("blacks", blacks.toDouble())
        json.put("fade", fade.toDouble())
        json.put("sharpenAmount", sharpenAmount.toDouble())
        
        val customJson = JSONObject()
        for ((key, value) in customParameters) {
            customJson.put(key, value.toDouble())
        }
        json.put("customParameters", customJson)
        return json
    }

    fun copyFrom(other: ColorAdjustment) {
        this.exposure = other.exposure
        this.brightness = other.brightness
        this.contrast = other.contrast
        this.saturation = other.saturation
        this.vibrance = other.vibrance
        this.temperature = other.temperature
        this.tint = other.tint
        this.gamma = other.gamma
        this.highlights = other.highlights
        this.shadows = other.shadows
        this.whites = other.whites
        this.blacks = other.blacks
        this.fade = other.fade
        this.sharpenAmount = other.sharpenAmount
        this.customParameters.clear()
        this.customParameters.putAll(other.customParameters)
    }

    companion object {
        fun fromJSONObject(json: JSONObject): ColorAdjustment {
            val adj = ColorAdjustment(
                exposure = json.optDouble("exposure", 0.0).toFloat(),
                brightness = json.optDouble("brightness", 0.0).toFloat(),
                contrast = json.optDouble("contrast", 1.0).toFloat(),
                saturation = json.optDouble("saturation", 1.0).toFloat(),
                vibrance = json.optDouble("vibrance", 1.0).toFloat(),
                temperature = json.optDouble("temperature", 0.0).toFloat(),
                tint = json.optDouble("tint", 0.0).toFloat(),
                gamma = json.optDouble("gamma", 1.0).toFloat(),
                highlights = json.optDouble("highlights", 0.0).toFloat(),
                shadows = json.optDouble("shadows", 0.0).toFloat(),
                whites = json.optDouble("whites", 0.0).toFloat(),
                blacks = json.optDouble("blacks", 0.0).toFloat(),
                fade = json.optDouble("fade", 0.0).toFloat(),
                sharpenAmount = json.optDouble("sharpenAmount", 0.0).toFloat()
            )
            val customJson = json.optJSONObject("customParameters")
            if (customJson != null) {
                val keys = customJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    adj.customParameters[key] = customJson.optDouble(key, 0.0).toFloat()
                }
            }
            return adj
        }
    }
}
