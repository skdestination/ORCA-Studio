package com.litecut.app.timeline

enum class ExportPreset {
    WEB_HD_H264,        // 1080p AVC (H.264) - Web standard, 30 FPS, high compatibility
    CINEMATIC_4K_H265,  // 4K UHD HEVC (H.265) - High quality, 24 FPS, cinematic VBR
    MOBILE_720P_PROXY,  // 720p HD AVC (H.264) - Lightweight preview, 30 FPS, CBR
    ULTRA_60FPS_REC2020, // 1080p HEVC (H.265) - 60 FPS, Rec.2020 Color Space, HDR
    EXTREME_8K_AV1      // 8K AV1 - Future-proof high-density streaming profile, 60 FPS
}
