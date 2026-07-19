package com.litecut.app.timeline

enum class KeyframeChannel(val propertyName: String, val defaultValue: Double) {
    POSITION_X("position_x", 0.0),
    POSITION_Y("position_y", 0.0),
    SCALE_X("scale_x", 1.0),
    SCALE_Y("scale_y", 1.0),
    ROTATION("rotation", 0.0),
    OPACITY("opacity", 1.0),
    VOLUME("volume", 1.0),
    SPEED("speed", 1.0),
    CROP("crop", 0.0),
    BLUR("blur", 0.0),
    SATURATION("saturation", 1.0),
    BRIGHTNESS("brightness", 1.0),
    CONTRAST("contrast", 1.0);

    companion object {
        fun fromPropertyName(name: String): KeyframeChannel? {
            return values().find { it.propertyName.lowercase() == name.lowercase() }
        }
    }
}
