package com.litecut.app.timeline

/**
 * Centralized theme and styling rules for the native video editing timeline.
 * Provides fine-grained customizable control over the aesthetic footprint.
 */
object TimelineTheme {
    // Sizing and Layout Boundaries
    var headerHeight: Float = 100f       // Height of the time ruler
    var trackHeight: Float = 130f        // Height of each track lane
    var trackSpacing: Float = 14f        // Spacing between tracks
    var clipCornerRadius: Float = 14f    // Corner radius of clip rectangles
    var clipInnerMargin: Float = 6f      // Top/bottom margins inside track lane

    // Core Colors
    var backgroundColor: Int = 0xFF101012.toInt()         // Dark canvas background
    var trackBackgroundColor: Int = 0xFF18181C.toInt()    // Subtle track lane background
    var trackSeparatorColor: Int = 0x1FFFFFFF             // Separator line between lanes
    var headerBackgroundColor: Int = 0xFF141418.toInt()   // Time ruler background
    var headerBorderColor: Int = 0x33FFFFFF               // Border line below ruler

    // Ruler Grid Colors
    var rulerTickColor: Int = 0x44FFFFFF                  // Tick marks color
    var rulerTextColor: Int = 0x88FFFFFF                  // Label colors
    var rulerTextSize: Float = 26f                        // Text size for ruler times

    // Clip Card Styling by Type
    var clipVideoColor: Int = 0xFF1E3A8A.toInt()          // Cinematic deep blue
    var clipAudioColor: Int = 0xFF4D1D95.toInt()          // Waveform deep violet
    var clipTextColor: Int = 0xFF78350F.toInt()           // Text layer warm amber
    var clipImageColor: Int = 0xFF064E3B.toInt()          // Static photo dark emerald
    var clipBorderColor: Int = 0x22FFFFFF                 // Default subtle clip border

    // Selection Highlight
    var selectionBorderColor: Int = 0xFFFFD700.toInt()    // Vibrant gold for active focus
    var selectionBorderWidth: Float = 6f                  // Thicker gold border for selections

    // Playhead Styling
    var playheadLineColor: Int = 0xFFFF2D55.toInt()       // Neon crimson indicator line
    var playheadHeadColor: Int = 0xFFFF2D55.toInt()       // Neon crimson pointer top
    var playheadLineWidth: Float = 4f                     // Playhead line width

    // Clip Label Font Settings
    var clipLabelColor: Int = 0xFFFFFFFF.toInt()          // Text on top of clips
    var clipLabelSize: Float = 28f                        // Font size of clip text label
}
