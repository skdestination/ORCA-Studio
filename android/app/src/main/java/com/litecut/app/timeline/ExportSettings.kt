package com.litecut.app.timeline

import org.json.JSONObject

enum class BitrateMode {
    VBR, // Variable Bitrate
    CBR  // Constant Bitrate
}

data class Resolution(
    val width: Int,
    val height: Int,
    val name: String
) {
    companion object {
        val SD_720P = Resolution(1280, 720, "720p HD")
        val FHD_1080P = Resolution(1920, 1080, "1080p Full HD")
        val QHD_1440P = Resolution(2560, 1440, "1440p QHD")
        val UHD_4K = Resolution(3840, 2160, "4K Ultra HD")
        val UHD_8K = Resolution(7680, 4320, "8K Extreme HD")
    }
}

data class ExportSettings(
    val id: String,
    var resolution: Resolution = Resolution.FHD_1080P,
    var frameRate: Int = 30, // 24, 25, 30, 50, 60, 90, 120 FPS
    var bitrateBps: Long = 15_000_000, // Bits per second (e.g. 15 Mbps)
    var bitrateMode: BitrateMode = BitrateMode.VBR,
    var videoCodec: String = "video/avc", // AVC (H.264), HEVC (H.265), AV1
    var audioCodec: String = "audio/mp4a-latm", // AAC, Opus
    var audioBitrateBps: Int = 192_000, // 192 kbps
    var audioSampleRate: Int = 44100, // 44.1kHz, 48kHz
    var isProxyExport: Boolean = false,
    var useHardwareAcceleration: Boolean = true,
    val colorSpace: ColorSpace = ColorSpace.REC709,
    val transferFunction: ColorTransferFunction = ColorTransferFunction.GAMMA_2_4
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("width", resolution.width)
        json.put("height", resolution.height)
        json.put("resolutionName", resolution.name)
        json.put("frameRate", frameRate)
        json.put("bitrateBps", bitrateBps)
        json.put("bitrateMode", bitrateMode.name)
        json.put("videoCodec", videoCodec)
        json.put("audioCodec", audioCodec)
        json.put("audioBitrateBps", audioBitrateBps)
        json.put("audioSampleRate", audioSampleRate)
        json.put("isProxyExport", isProxyExport)
        json.put("useHardwareAcceleration", useHardwareAcceleration)
        json.put("colorSpace", colorSpace.name)
        json.put("transferFunction", transferFunction.name)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): ExportSettings {
            return ExportSettings(
                id = json.getString("id"),
                resolution = Resolution(
                    width = json.optInt("width", 1920),
                    height = json.optInt("height", 1080),
                    name = json.optString("resolutionName", "1080p Full HD")
                ),
                frameRate = json.optInt("frameRate", 30),
                bitrateBps = json.optLong("bitrateBps", 15_000_000),
                bitrateMode = BitrateMode.valueOf(json.optString("bitrateMode", BitrateMode.VBR.name)),
                videoCodec = json.optString("videoCodec", "video/avc"),
                audioCodec = json.optString("audioCodec", "audio/mp4a-latm"),
                audioBitrateBps = json.optInt("audioBitrateBps", 192_000),
                audioSampleRate = json.optInt("audioSampleRate", 44100),
                isProxyExport = json.optBoolean("isProxyExport", false),
                useHardwareAcceleration = json.optBoolean("useHardwareAcceleration", true),
                colorSpace = ColorSpace.valueOf(json.optString("colorSpace", ColorSpace.REC709.name)),
                transferFunction = ColorTransferFunction.valueOf(json.optString("transferFunction", ColorTransferFunction.GAMMA_2_4.name))
            )
        }
    }
}
