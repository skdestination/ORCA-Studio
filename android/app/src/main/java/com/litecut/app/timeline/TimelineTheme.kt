package com.litecut.app.timeline

/**
 * Centralized theme and styling rules for the native video editing timeline matching React.
 */
object TimelineTheme {
    // Sizing and Layout Boundaries matching React App
    var headerHeight: Float = 15f        // Height of the time ruler (15dp in React)
    var trackHeight: Float = 38f         // Height of each track lane (38dp in React)
    var trackSpacing: Float = 0f         // Spacing between tracks (0dp, thin 1px border)
    var clipCornerRadius: Float = 8f     // Corner radius of video/text/image clips (8dp in React)
    var audioClipCornerRadius: Float = 12f // Corner radius of audio clips (12dp in React)
    var clipInnerMargin: Float = 2f       // Top/bottom margin inside track lane (2dp)

    // Core Colors matching React App (#0c0c0e)
    var backgroundColor: Int = (0xFF0C0C0EL).toInt()         // Dark canvas background
    var trackBackgroundColor: Int = (0xFF0C0C0EL).toInt()    // Subtle track lane background
    var trackSeparatorColor: Int = 0x0AFFFFFF             // 3% opacity white border line between lanes
    var headerBackgroundColor: Int = (0xF20C0C0EL).toInt()   // Time ruler background (95% opacity)
    var headerBorderColor: Int = 0x0DFFFFFF               // Border line below ruler

    // Ruler Grid Colors
    var rulerTickColor: Int = 0x33FFFFFF                  // Tick marks color
    var rulerTextColor: Int = (0x88A1A1AAL).toInt()          // Label colors (zinc-400)
    var rulerTextSize: Float = 10f                        // Text size for ruler times (10sp)

    // Unselected Clip Card Gradients (bg-gradient-to-r)
    var clipVideoStartColor: Int = (0xFF0D1E3DL).toInt()
    var clipVideoEndColor: Int = (0xFF122B5EL).toInt()
    var clipAudioStartColor: Int = (0xFF21103DL).toInt()
    var clipAudioEndColor: Int = (0xFF3B1263L).toInt()
    var clipTextStartColor: Int = (0xFF441F05L).toInt()
    var clipTextEndColor: Int = (0xFF632900L).toInt()
    var clipImageStartColor: Int = (0xFF032A19L).toInt()
    var clipImageEndColor: Int = (0xFF0C4029L).toInt()

    // Selected Clip Card Gradients
    var clipVideoSelStartColor: Int = (0xFF1E40AFL).toInt()
    var clipVideoSelEndColor: Int = (0xFF1D4ED8L).toInt()
    var clipAudioSelStartColor: Int = (0xFF5A21B3L.toInt())
    var clipAudioSelEndColor: Int = (0xFF4C1D95L).toInt()
    var clipTextSelStartColor: Int = (0xFFB45309L).toInt()
    var clipTextSelEndColor: Int = (0xFFD97706L).toInt()
    var clipImageSelStartColor: Int = (0xFF047857L).toInt()
    var clipImageSelEndColor: Int = (0xFF065F46L).toInt()

    // Borders
    var clipBorderColorVideo: Int = 0x336366F1
    var clipBorderColorAudio: Int = 0x33A855F7
    var clipBorderColorText: Int = 0x33F59E0B
    var clipBorderColorImage: Int = 0x3310B981

    var selBorderColorVideo: Int = (0xFF60A5FAL).toInt()
    var selBorderColorAudio: Int = (0xFFC084FCL).toInt()
    var selBorderColorText: Int = (0xFFFBBF24L).toInt()
    var selBorderColorImage: Int = (0xFF34D399L).toInt()

    // Playhead Styling
    var playheadLineColor: Int = (0xFFFF2D55L).toInt()       // Crimson indicator line
    var playheadHeadColor: Int = (0xFFFF2D55L).toInt()       // Crimson pointer top
    var playheadLineWidth: Float = 1f                     // Playhead line width (1px in React)

    // Convenience properties for TimelineRenderer
    val clipVideoColor: Int get() = clipVideoStartColor
    val clipAudioColor: Int get() = clipAudioStartColor
    val clipTextColor: Int get() = clipTextStartColor
    val clipImageColor: Int get() = clipImageStartColor
    val clipBorderColor: Int get() = clipBorderColorVideo
    val selectionBorderColor: Int get() = selBorderColorVideo
    var selectionBorderWidth: Float = 2f

    // Clip Label Font Settings
    var clipLabelColor: Int = (0xFFFFFFFFL).toInt()          // Text on top of clips
    var clipLabelSize: Float = 10f                        // Font size of clip text label
}

