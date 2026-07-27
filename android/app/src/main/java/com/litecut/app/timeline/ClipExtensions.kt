package com.litecut.app.timeline

var Clip.opacity: Float
    get() = (additionalProperties["opacity"] as? Number)?.toFloat() ?: 1.0f
    set(value) { additionalProperties["opacity"] = value }

var Clip.scale: Float
    get() = (additionalProperties["scale"] as? Number)?.toFloat() ?: 1.0f
    set(value) { additionalProperties["scale"] = value }

var Clip.rotation: Float
    get() = (additionalProperties["rotation"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["rotation"] = value }

var Clip.mute: Boolean
    get() = (additionalProperties["mute"] as? Boolean) ?: false
    set(value) { additionalProperties["mute"] = value }

var Clip.text: String
    get() = (additionalProperties["text"] as? String) ?: (name ?: "")
    set(value) { additionalProperties["text"] = value }

var Clip.volume: Float
    get() = (additionalProperties["volume"] as? Number)?.toFloat() ?: 1.0f
    set(value) { additionalProperties["volume"] = value }

var Clip.brightness: Float
    get() = (additionalProperties["brightness"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["brightness"] = value }

var Clip.contrast: Float
    get() = (additionalProperties["contrast"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["contrast"] = value }

var Clip.saturation: Float
    get() = (additionalProperties["saturation"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["saturation"] = value }

var Clip.temperature: Float
    get() = (additionalProperties["temperature"] as? Number)?.toFloat() ?: 0.0f
    set(value) { additionalProperties["temperature"] = value }
