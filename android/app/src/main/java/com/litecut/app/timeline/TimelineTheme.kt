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
    var backgroundColor: Int = 0xFF0C0C0E.toInt()         // Dark canvas background
    var trackBackgroundColor: Int = 0xFF0C0C0E.toInt()    // Subtle track lane background
    var trackSeparatorColor: Int = 0x0AFFFFFF             // 3% opacity white border line between lanes
    var headerBackgroundColor: Int = 0xF20C0C0E.toInt()   // Time ruler background (95% opacity)
    var headerBorderColor: Int = 0x0DFFFFFF               // Border line below ruler

    // Ruler Grid Colors
    var rulerTickColor: Int = 0x33FFFFFF                  // Tick marks color
    var rulerTextColor: Int = 0x88A1A1AA.toInt()          // Label colors (zinc-400)
    var rulerTextSize: Float = 10f                        // Text size for ruler times (10sp)

    // Unselected Clip Card Gradients (bg-gradient-to-r)
    var clipVideoStartColor: Int = 0xFF0D1E3D.toInt()
    var clipVideoEndColor: Int = 0xFF122B5E.toInt()
    var clipAudioStartColor: Int = 0xFF21103D.toInt()
    var clipAudioEndColor: Int = 0xFF3B1263.toInt()
    var clipTextStartColor: Int = 0xFF441F05.toInt()
    var clipTextEndColor: Int = 0xFF632900.toInt()
    var clipImageStartColor: Int = 0xFF032A19.toInt()
    var clipImageEndColor: Int = 0xFF0C4029.toInt()

    // Selected Clip Card Gradients
    var clipVideoSelStartColor: Int = 0xFF1E40AF.toInt()
    var clipVideoSelEndColor: Int = 0xFF1D4ED8.toInt()
    var clipAudioSelStartColor: Int = 0xFF5A21B3.toInt()
    var clipAudioSelEndColor: Int = 0xFF4C1D95.toInt()
    var clipTextSelStartColor: Int = 0xFFB45309.toInt()
    var clipTextSelEndColor: Int = 0xFFD97706.toInt()
    var clipImageSelStartColor: Int = 0xFF047857.toInt()
    var clipImageSelEndColor: Int = 0xFF065F46.toInt()

    // Borders
    var clipBorderColorVideo: Int = 0x336366F1.toInt() // indigo-500/20
    var clipBorderColorAudio: Int = 0x33A855F7.toInt() // purple-500/20
    var clipBorderColorText: Int = 0x33F59E0B.toInt()  // amber-500/20
    var clipBorderColorImage: Int = 0x3310B981.toInt() // emerald-500/20

    var selBorderColorVideo: Int = 0xFF60A5FA.toInt()  // blue-400
    var selBorderColorAudio: Int = 0xFFC084FC.toInt()  // purple-400
    var selBorderColorText: Int = 0xFFFBBF24.toInt()   // amber-400
    var selBorderColorImage: Int = 0xFF34D399.toInt()  // emerald-400

    // Playhead Styling
    var playheadLineColor: Int = 0xFFFF2D55.toInt()       // Crimson indicator line
    var playheadHeadColor: Int = 0xFFFF2D55.toInt()       // Crimson pointer top
    var playheadLineWidth: Float = 1f                     // Playhead line width (1px in React)

    // Clip Label Font Settings
    var clipLabelColor: Int = 0xFFFFFFFF.toInt()          // Text on top of clips
    var clipLabelSize: Float = 10f                        // Font size of clip text label
}

