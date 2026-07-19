package com.litecut.app.timeline

import org.json.JSONObject

data class ExportProfile(
    val name: String,
    val description: String,
    val preset: ExportPreset,
    val isCustom: Boolean = false
) {
    fun createSettings(id: String): ExportSettings {
        return when (preset) {
            ExportPreset.WEB_HD_H264 -> ExportSettings(
                id = id,
                resolution = Resolution.FHD_1080P,
                frameRate = 30,
                bitrateBps = 12_000_000, // 12 Mbps
                bitrateMode = BitrateMode.VBR,
                videoCodec = "video/avc",
                audioCodec = "audio/mp4a-latm",
                audioBitrateBps = 192_000,
                audioSampleRate = 44100,
                colorSpace = ColorSpace.REC709,
                transferFunction = ColorTransferFunction.GAMMA_2_4
            )
            ExportPreset.CINEMATIC_4K_H265 -> ExportSettings(
                id = id,
                resolution = Resolution.UHD_4K,
                frameRate = 24,
                bitrateBps = 45_000_000, // 45 Mbps
                bitrateMode = BitrateMode.VBR,
                videoCodec = "video/hevc",
                audioCodec = "audio/mp4a-latm",
                audioBitrateBps = 320_000,
                audioSampleRate = 48000,
                colorSpace = ColorSpace.DCI_P3,
                transferFunction = ColorTransferFunction.GAMMA_2_4
            )
            ExportPreset.MOBILE_720P_PROXY -> ExportSettings(
                id = id,
                resolution = Resolution.SD_720P,
                frameRate = 30,
                bitrateBps = 4_000_000, // 4 Mbps
                bitrateMode = BitrateMode.CBR,
                videoCodec = "video/avc",
                audioCodec = "audio/mp4a-latm",
                audioBitrateBps = 128_000,
                audioSampleRate = 44100,
                isProxyExport = true,
                colorSpace = ColorSpace.SRGB,
                transferFunction = ColorTransferFunction.GAMMA_2_2
            )
            ExportPreset.ULTRA_60FPS_REC2020 -> ExportSettings(
                id = id,
                resolution = Resolution.FHD_1080P,
                frameRate = 60,
                bitrateBps = 20_000_000, // 20 Mbps
                bitrateMode = BitrateMode.VBR,
                videoCodec = "video/hevc",
                audioCodec = "audio/mp4a-latm",
                audioBitrateBps = 256_000,
                audioSampleRate = 48000,
                colorSpace = ColorSpace.REC2020,
                transferFunction = ColorTransferFunction.PQ
            )
            ExportPreset.EXTREME_8K_AV1 -> ExportSettings(
                id = id,
                resolution = Resolution.UHD_8K,
                frameRate = 60,
                bitrateBps = 100_000_000, // 100 Mbps
                bitrateMode = BitrateMode.VBR,
                videoCodec = "video/av1",
                audioCodec = "audio/opus",
                audioBitrateBps = 320_000,
                audioSampleRate = 48000,
                colorSpace = ColorSpace.REC2020,
                transferFunction = ColorTransferFunction.PQ
            )
        }
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("description", description)
        json.put("preset", preset.name)
        json.put("isCustom", isCustom)
        return json
    }

    companion object {
        val ALL_PROFILES = listOf(
            ExportProfile("Web HD 1080p (Default)", "Highly compatible standard MP4 format suitable for YouTube, Instagram and general web sharing.", ExportPreset.WEB_HD_H264),
            ExportProfile("Cinematic 4K UHD", "Ultra High Definition HEVC video rendered at 24 FPS with DCI-P3 wide color gamut profile.", ExportPreset.CINEMATIC_4K_H265),
            ExportProfile("Fast Proxy 720p", "Low footprint fast rendering settings. Perfect for drafts, preview sharing or proxy media generation.", ExportPreset.MOBILE_720P_PROXY),
            ExportProfile("60FPS HDR Broadcast", "Smooth high-framerate dynamic range video with Rec. 2020 BT.2100 transfer profile.", ExportPreset.ULTRA_60FPS_REC2020),
            ExportProfile("Extreme 8K AV1", "Super-density next-generation compression standard designed for 8K master catalog archives.", ExportPreset.EXTREME_8K_AV1)
        )

        fun fromJSONObject(json: JSONObject): ExportProfile {
            return ExportProfile(
                name = json.getString("name"),
                description = json.getString("description"),
                preset = ExportPreset.valueOf(json.getString("preset")),
                isCustom = json.optBoolean("isCustom", false)
            )
        }
    }
}
