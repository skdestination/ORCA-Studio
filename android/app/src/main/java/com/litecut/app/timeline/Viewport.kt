package com.litecut.app.timeline

class Viewport {
    var scrollX: Double = 0.0 // horizontal scroll offset in pixels
    var scrollY: Double = 0.0 // vertical scroll offset in pixels
    
    var width: Int = 0
    var height: Int = 0

    var minZoom: Double = 0.1
    var maxZoom: Double = 10.0

    fun updateSize(w: Int, h: Int) {
        width = w
        height = h
    }

    fun handleScroll(dx: Float, dy: Float) {
        scrollX = (scrollX + dx).coerceAtLeast(0.0)
        scrollY = (scrollY + dy).coerceAtLeast(0.0)
    }

    /**
     * Determines whether a clip is currently within the visible horizontal boundary of the viewport.
     * Ready for virtualization.
     */
    fun isClipVisible(clip: Clip, pixelsPerSecond: Double): Boolean {
        val leftPx = clip.leftSeconds * pixelsPerSecond
        val rightPx = (clip.leftSeconds + clip.durationSeconds) * pixelsPerSecond
        return rightPx >= scrollX && leftPx <= scrollX + width
    }
}
