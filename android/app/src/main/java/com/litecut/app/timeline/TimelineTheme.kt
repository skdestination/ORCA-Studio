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

    // Clip Card Styling by Type matching React Preview
    var clipVideoColor: Int = 0xFF0D1E3D.toInt()          // Deep blue video clip gradient start
    var clipVideoEndColor: Int = 0xFF122B5E.toInt()       // Deep blue video clip gradient end
    var clipAudioColor: Int = 0xFF21103D.toInt()          // Deep purple audio clip gradient start
    var clipAudioEndColor: Int = 0xFF3B1263.toInt()       // Deep purple audio clip gradient end
    var clipTextColor: Int = 0xFF441F05.toInt()           // Warm amber text clip gradient start
    var clipTextEndColor: Int = 0xFF632900.toInt()        // Warm amber text clip gradient end
    var clipImageColor: Int = 0xFF032A19.toInt()          // Dark emerald image clip gradient start
    var clipImageEndColor: Int = 0xFF0C4029.toInt()       // Dark emerald image clip gradient end
    var clipBorderColor: Int = 0x1AFFFFFF                 // Subtle border for unselected clips

    // Selection Highlight
    var selectionBorderColor: Int = 0xFF60A5FA.toInt()    // Vibrant indigo/blue highlight (#60A5FA)
    var selectionBorderWidth: Float = 2f                  // Border width for selections

    // Playhead Styling
    var playheadLineColor: Int = 0xFFFF2D55.toInt()       // Crimson indicator line
    var playheadHeadColor: Int = 0xFFFF2D55.toInt()       // Crimson pointer top
    var playheadLineWidth: Float = 1f                     // Playhead line width (1px in React)

    // Clip Label Font Settings
    var clipLabelColor: Int = 0xFFFFFFFF.toInt()          // Text on top of clips
    var clipLabelSize: Float = 10f                        // Font size of clip text label
}

