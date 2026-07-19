package com.litecut.app.timeline

enum class ColorSpace {
    SRGB,
    REC709,
    DCI_P3,
    REC2020,
    ACES_CG,
    ACES_CC,
    LINEAR
}

enum class ColorTransferFunction {
    GAMMA_2_2,
    GAMMA_2_4,
    PQ, // HDR10 / Dolby Vision
    HLG,
    LOG_C,
    S_LOG3,
    V_LOG,
    D_LOG,
    LINEAR
}

data class ColorProfile(
    val id: String,
    val name: String,
    val colorSpace: ColorSpace = ColorSpace.REC709,
    val transferFunction: ColorTransferFunction = ColorTransferFunction.GAMMA_2_4,
    val isHdr: Boolean = false,
    val maxNits: Float = 100.0f
) {
    companion object {
        val REC709_SDR = ColorProfile("rec709_sdr", "Rec.709 SDR", ColorSpace.REC709, ColorTransferFunction.GAMMA_2_4, false, 100.0f)
        val SRGB_SDR = ColorProfile("srgb_sdr", "sRGB SDR", ColorSpace.SRGB, ColorTransferFunction.GAMMA_2_2, false, 80.0f)
        val REC2020_HDR10 = ColorProfile("rec2020_hdr10", "Rec.2020 HDR10 (PQ)", ColorSpace.REC2020, ColorTransferFunction.PQ, true, 1000.0f)
        val ACES_FILMIC = ColorProfile("aces_filmic", "ACES Filmic Log", ColorSpace.ACES_CG, ColorTransferFunction.LOG_C, true, 4000.0f)
    }
}
