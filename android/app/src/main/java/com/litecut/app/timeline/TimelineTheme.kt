package com.litecut.app.timeline

/**
 * Centralized theme and styling rules for the native video editing timeline matching React.
 */
object TimelineTheme {
    // Sizing and Layout Boundaries
    var headerHeight: Float = 48f        // Height of the time ruler
    var trackHeight: Float = 60f         // Height of each track lane
    var trackSpacing: Float = 8f         // Spacing between tracks
    var clipCornerRadius: Float = 12f     // Corner radius of clip rectangles
    var clipInnerMargin: Float = 4f       // Top/bottom margins inside track lane

    // Core Colors
    var backgroundColor: Int = 0xFF09090B.toInt()         // Dark canvas background
    var trackBackgroundColor: Int = 0xFF0D0D10.toInt()    // Subtle track lane background
    var trackSeparatorColor: Int = 0x1AFFFFFF             // Separator line between lanes
    var headerBackgroundColor: Int = 0xFF0D0D10.toInt()   // Time ruler background
    var headerBorderColor: Int = 0x1AFFFFFF               // Border line below ruler

    // Ruler Grid Colors
    var rulerTickColor: Int = 0x44FFFFFF                  // Tick marks color
    var rulerTextColor: Int = 0x88FFFFFF.toInt()          // Label colors
    var rulerTextSize: Float = 22f                        // Text size for ruler times

    // Clip Card Styling by Type matching React Image 2
    var clipVideoColor: Int = 0xFF0E2144.toInt()          // Deep blue video clip
    var clipAudioColor: Int = 0xFF2F1154.toInt()          // Deep purple audio clip
    var clipTextColor: Int = 0xFF4A1C00.toInt()           // Warm amber text clip
    var clipImageColor: Int = 0xFF052B1B.toInt()          // Dark emerald image clip
    var clipBorderColor: Int = 0x333B82F6.toInt()         // Default subtle blue/teal border

    // Selection Highlight
    var selectionBorderColor: Int = 0xFF60A5FA.toInt()    // Vibrant blue highlight
    var selectionBorderWidth: Float = 3f                  // Border width for selections

    // Playhead Styling
    var playheadLineColor: Int = 0xFFFF2D55.toInt()       // Neon crimson indicator line
    var playheadHeadColor: Int = 0xFFFF2D55.toInt()       // Neon crimson pointer top
    var playheadLineWidth: Float = 2f                     // Playhead line width

    // Clip Label Font Settings
    var clipLabelColor: Int = 0xFFFFFFFF.toInt()          // Text on top of clips
    var clipLabelSize: Float = 22f                        // Font size of clip text label
}
