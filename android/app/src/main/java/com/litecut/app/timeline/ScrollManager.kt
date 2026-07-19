package com.litecut.app.timeline

class ScrollManager {
    var scrollX: Double = 0.0
        private set
    var scrollY: Double = 0.0
        private set
        
    var viewportWidth: Double = 1080.0
    var viewportHeight: Double = 500.0

    fun updateScroll(x: Double, y: Double) {
        scrollX = x.coerceAtLeast(0.0)
        scrollY = y.coerceAtLeast(0.0)
    }

    /**
     * Updates viewport scroll offset if a clip is being dragged near the edges of the visible timeline.
     */
    fun autoScrollIfNeeded(draggedX: Double, thresholdPx: Double = 60.0, speedPx: Double = 12.0) {
        if (draggedX < scrollX + thresholdPx) {
            scrollX = (scrollX - speedPx).coerceAtLeast(0.0)
        } else if (draggedX > scrollX + viewportWidth - thresholdPx) {
            scrollX += speedPx
        }
    }

    fun isClipVisibleInViewport(clip: TimelineClip, pixelsPerSecond: Double): Boolean {
        val clipLeftPx = clip.startTime * pixelsPerSecond
        val clipRightPx = (clip.startTime + clip.duration) * pixelsPerSecond
        return clipRightPx >= scrollX && clipLeftPx <= scrollX + viewportWidth
    }
}
