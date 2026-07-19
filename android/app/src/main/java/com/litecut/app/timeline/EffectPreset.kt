package com.litecut.app.timeline

object EffectPreset {
    fun createDefaultEffect(id: String, type: EffectType): Effect {
        val effect = Effect(id = id, type = type, name = getFriendlyName(type))
        setupDefaultParameters(effect)
        return effect
    }

    private fun getFriendlyName(type: EffectType): String {
        return when (type) {
            EffectType.GAUSSIAN_BLUR -> "Gaussian Blur"
            EffectType.DIRECTIONAL_BLUR -> "Directional Blur"
            EffectType.MOTION_BLUR -> "Motion Blur"
            EffectType.RADIAL_BLUR -> "Radial Blur"
            EffectType.GLOW -> "Glow"
            EffectType.BLOOM -> "Bloom"
            EffectType.SHARPEN -> "Sharpen"
            EffectType.EMBOSS -> "Emboss"
            EffectType.NOISE -> "Noise"
            EffectType.FILM_GRAIN -> "Film Grain"
            EffectType.VIGNETTE -> "Vignette"
            EffectType.CHROMATIC_ABERRATION -> "Chromatic Aberration"
            EffectType.RGB_SPLIT -> "RGB Split"
            EffectType.LENS_DISTORTION -> "Lens Distortion"
            EffectType.PIXELATE -> "Pixelate"
            EffectType.MOSAIC -> "Mosaic"
            EffectType.AI_EFFECT -> "AI Intelligent Enhancer"
            EffectType.CUSTOM -> "Custom Plug-in Effect"
        }
    }

    private fun setupDefaultParameters(effect: Effect) {
        when (effect.type) {
            EffectType.GAUSSIAN_BLUR -> {
                effect.parameters["radius"] = EffectParameter("radius", 10.0f, 10.0f, 0.0f, 100.0f)
                effect.parameters["iterations"] = EffectParameter("iterations", 3.0f, 3.0f, 1.0f, 10.0f)
            }
            EffectType.DIRECTIONAL_BLUR -> {
                effect.parameters["length"] = EffectParameter("length", 15.0f, 15.0f, 0.0f, 100.0f)
                effect.parameters["angle"] = EffectParameter("angle", 0.0f, 0.0f, -180.0f, 180.0f)
            }
            EffectType.MOTION_BLUR -> {
                effect.parameters["shutter_angle"] = EffectParameter("shutter_angle", 180.0f, 180.0f, 0.0f, 360.0f)
                effect.parameters["samples"] = EffectParameter("samples", 8.0f, 8.0f, 2.0f, 32.0f)
            }
            EffectType.RADIAL_BLUR -> {
                effect.parameters["amount"] = EffectParameter("amount", 5.0f, 5.0f, 0.0f, 50.0f)
                effect.parameters["center_x"] = EffectParameter("center_x", 0.5f, 0.5f, 0.0f, 1.0f)
                effect.parameters["center_y"] = EffectParameter("center_y", 0.5f, 0.5f, 0.0f, 1.0f)
            }
            EffectType.GLOW -> {
                effect.parameters["intensity"] = EffectParameter("intensity", 0.5f, 0.5f, 0.0f, 2.0f)
                effect.parameters["radius"] = EffectParameter("radius", 20.0f, 20.0f, 0.0f, 100.0f)
                effect.parameters["threshold"] = EffectParameter("threshold", 0.8f, 0.8f, 0.0f, 1.0f)
            }
            EffectType.BLOOM -> {
                effect.parameters["intensity"] = EffectParameter("intensity", 0.4f, 0.4f, 0.0f, 2.0f)
                effect.parameters["threshold"] = EffectParameter("threshold", 0.7f, 0.7f, 0.0f, 1.0f)
                effect.parameters["softness"] = EffectParameter("softness", 0.5f, 0.5f, 0.0f, 1.0f)
            }
            EffectType.SHARPEN -> {
                effect.parameters["amount"] = EffectParameter("amount", 0.3f, 0.3f, 0.0f, 1.0f)
            }
            EffectType.EMBOSS -> {
                effect.parameters["intensity"] = EffectParameter("intensity", 0.5f, 0.5f, 0.0f, 1.0f)
            }
            EffectType.NOISE -> {
                effect.parameters["amount"] = EffectParameter("amount", 0.05f, 0.05f, 0.0f, 0.5f)
                effect.parameters["is_color"] = EffectParameter("is_color", 0.0f, 0.0f, 0.0f, 1.0f) // 0=Mono, 1=Color
            }
            EffectType.FILM_GRAIN -> {
                effect.parameters["intensity"] = EffectParameter("intensity", 0.1f, 0.1f, 0.0f, 1.0f)
                effect.parameters["size"] = EffectParameter("size", 1.5f, 1.5f, 0.5f, 5.0f)
                effect.parameters["roughness"] = EffectParameter("roughness", 0.5f, 0.5f, 0.0f, 1.0f)
            }
            EffectType.VIGNETTE -> {
                effect.parameters["amount"] = EffectParameter("amount", 0.4f, 0.4f, 0.0f, 1.0f)
                effect.parameters["feather"] = EffectParameter("feather", 0.6f, 0.6f, 0.0f, 1.0f)
                effect.parameters["roundness"] = EffectParameter("roundness", 1.0f, 1.0f, 0.0f, 2.0f)
            }
            EffectType.CHROMATIC_ABERRATION -> {
                effect.parameters["fringe_amount"] = EffectParameter("fringe_amount", 5.0f, 5.0f, 0.0f, 50.0f)
            }
            EffectType.RGB_SPLIT -> {
                effect.parameters["offset_red_x"] = EffectParameter("offset_red_x", 2.0f, 2.0f, -20.0f, 20.0f)
                effect.parameters["offset_red_y"] = EffectParameter("offset_red_y", 0.0f, 0.0f, -20.0f, 20.0f)
                effect.parameters["offset_blue_x"] = EffectParameter("offset_blue_x", -2.0f, -2.0f, -20.0f, 20.0f)
                effect.parameters["offset_blue_y"] = EffectParameter("offset_blue_y", 0.0f, 0.0f, -20.0f, 20.0f)
            }
            EffectType.LENS_DISTORTION -> {
                effect.parameters["k1"] = EffectParameter("k1", -0.1f, -0.1f, -1.0f, 1.0f)
                effect.parameters["k2"] = EffectParameter("k2", 0.0f, 0.0f, -1.0f, 1.0f)
            }
            EffectType.PIXELATE -> {
                effect.parameters["size_x"] = EffectParameter("size_x", 10.0f, 10.0f, 1.0f, 200.0f)
                effect.parameters["size_y"] = EffectParameter("size_y", 10.0f, 10.0f, 1.0f, 200.0f)
            }
            EffectType.MOSAIC -> {
                effect.parameters["count_x"] = EffectParameter("count_x", 40.0f, 40.0f, 4.0f, 500.0f)
                effect.parameters["count_y"] = EffectParameter("count_y", 30.0f, 30.0f, 4.0f, 500.0f)
            }
            EffectType.AI_EFFECT -> {
                effect.parameters["denoise_strength"] = EffectParameter("denoise_strength", 0.5f, 0.5f, 0.0f, 1.0f)
                effect.parameters["super_resolution"] = EffectParameter("super_resolution", 0.0f, 0.0f, 0.0f, 1.0f)
            }
            EffectType.CUSTOM -> {
                effect.parameters["intensity"] = EffectParameter("intensity", 1.0f, 1.0f, 0.0f, 1.0f)
            }
        }
    }
}
