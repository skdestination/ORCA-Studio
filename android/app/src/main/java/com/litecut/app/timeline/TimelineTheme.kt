package com.litecut.app.timeline

import android.content.Context

/**
 * Centralized theme and styling rules for the native video editing timeline matching React.
 */
object TimelineTheme {
    // Sizing and Layout Boundaries in DP
    var headerHeightDp: Float = 24f        // Height of the time ruler (24dp)
    var trackHeightDp: Float = 48f         // Height of each track lane (48dp)
    var trackSpacingDp: Float = 0f         // Spacing between tracks (0dp)

    // Pixel dimensions calculated from screen density
    var density: Float = 2.5f
    var headerHeight: Float = 24f * 2.5f
    var trackHeight: Float = 48f * 2.5f
    var trackSpacing: Float = 0f
    var clipCornerRadius: Float = 8f * 2.5f
    var audioClipCornerRadius: Float = 12f * 2.5f
    var clipInnerMargin: Float = 2f * 2.5f

    fun init(context: Context) {
        density = context.resources.displayMetrics.density
        headerHeight = headerHeightDp * density
        trackHeight = trackHeightDp * density
        trackSpacing = trackSpacingDp * density
        clipCornerRadius = 8f * density
        audioClipCornerRadius = 12f * density
        clipInnerMargin = 2f * density
        rulerTextSize = 10f * density
        clipLabelSize = 11f * density
        playheadLineWidth = 1.5f * density
    }

    // Core Colors matching React App (#0c0c0e)
    var backgroundColor: Int = 0xFF0C0C0E.toInt()         // Dark canvas background
    var trackBackgroundColor: Int = 0xFF0C0C0E.toInt()    // Subtle track lane background
    var trackSeparatorColor: Int = 0x0AFFFFFF             // 3% opacity white border line between lanes
    var headerBackgroundColor: Int = 0xF20C0C0E.toInt()   // Time ruler background (95% opacity)
    var headerBorderColor: Int = 0x0DFFFFFF               // Border line below ruler

    // Ruler Grid Colors
    var rulerTickColor: Int = 0x33FFFFFF                  // Tick marks color
    var rulerTextColor: Int = 0x88A1A1AA.toInt()          // Label colors (zinc-400)
    var rulerTextSize: Float = 10f * 2.5f                 // Text size for ruler times

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
    var clipBorderColorVideo: Int = 0x336366F1
    var clipBorderColorAudio: Int = 0x33A855F7
    var clipBorderColorText: Int = 0x33F59E0B
    var clipBorderColorImage: Int = 0x3310B981

    var selBorderColorVideo: Int = 0xFF60A5FA.toInt()
    var selBorderColorAudio: Int = 0xFFC084FC.toInt()
    var selBorderColorText: Int = 0xFFFBBF24.toInt()
    var selBorderColorImage: Int = 0xFF34D399.toInt()

    // Playhead Styling
    var playheadLineColor: Int = 0xFFFF2D55.toInt()       // Crimson indicator line
    var playheadHeadColor: Int = 0xFFFF2D55.toInt()       // Crimson pointer top
    var playheadLineWidth: Float = 1.5f * 2.5f            // Playhead line width

    // Convenience properties for TimelineRenderer
    val clipVideoColor: Int get() = clipVideoStartColor
    val clipAudioColor: Int get() = clipAudioStartColor
    val clipTextColor: Int get() = clipTextStartColor
    val clipImageColor: Int get() = clipImageStartColor
    val clipBorderColor: Int get() = clipBorderColorVideo
    val selectionBorderColor: Int get() = selBorderColorVideo
    var selectionBorderWidth: Float = 2f

    // Clip Label Font Settings
    var clipLabelColor: Int = 0xFFFFFFFF.toInt()          // Text on top of clips
    var clipLabelSize: Float = 11f * 2.5f                 // Font size of clip text label
}


