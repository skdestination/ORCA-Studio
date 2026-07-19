package com.litecut.app.timeline

import android.graphics.RectF

/**
 * An immutable frame descriptor from the consumer's perspective, representing a single frame of composition.
 * Uses an internal object pool (`PreviewFrame.obtain()` and `PreviewFrame.release()`) to eliminate heap
 * allocations in the hot playback loop (120 FPS).
 */
class PreviewFrame private constructor() {
    
    // Unique frame sequence identifier
    var id: Long = 0L
        private set
        
    // Time in seconds on the timeline
    var timeSeconds: Double = 0.0
        private set
        
    // Integer frame index corresponding to target FPS
    var frameIndex: Long = 0L
        private set
        
    // Whether this frame is rendered using low-res proxy source files
    var isProxy: Boolean = false
        private set
        
    // Viewport layout width in pixels
    var viewportWidth: Int = 0
        private set
        
    // Viewport layout height in pixels
    var viewportHeight: Int = 0
        private set
        
    // Adaptive quality multiplier (0.5f - 1.0f) based on frame dropped metrics
    var qualityScale: Float = 1.0f
        private set
        
    // High Dynamic Range (HDR) flag for ultra-high-fidelity color spaces
    var isHDR: Boolean = false
        private set
        
    // Frame invalidation flag indicating whether a recomposition pass is required
    var isDirty: Boolean = true
        private set
        
    // Reusable bounding box defining coordinates that require update
    val dirtyRegion = RectF()

    // Composed stream output associated with this frame
    val compositionOutput = CompositionOutput()

    companion object {
        private val pool = java.util.concurrent.ConcurrentLinkedQueue<PreviewFrame>()

        /**
         * Leases a clean PreviewFrame instance from the thread-safe concurrent pool.
         */
        fun obtain(
            id: Long,
            timeSeconds: Double,
            frameIndex: Long,
            isProxy: Boolean,
            viewportWidth: Int,
            viewportHeight: Int,
            qualityScale: Float,
            isHDR: Boolean,
            isDirty: Boolean
        ): PreviewFrame {
            val frame = pool.poll() ?: PreviewFrame()
            frame.id = id
            frame.timeSeconds = timeSeconds
            frame.frameIndex = frameIndex
            frame.isProxy = isProxy
            frame.viewportWidth = viewportWidth
            frame.viewportHeight = viewportHeight
            frame.qualityScale = qualityScale
            frame.isHDR = isHDR
            frame.isDirty = isDirty
            frame.dirtyRegion.set(0f, 0f, viewportWidth.toFloat(), viewportHeight.toFloat())
            return frame
        }

        /**
         * Returns the frame to the concurrent pool for future reuse.
         */
        fun release(frame: PreviewFrame) {
            frame.reset()
            pool.offer(frame)
        }
    }

    private fun reset() {
        id = 0L
        timeSeconds = 0.0
        frameIndex = 0L
        isProxy = false
        viewportWidth = 0
        viewportHeight = 0
        qualityScale = 1.0f
        isHDR = false
        isDirty = true
        dirtyRegion.set(0f, 0f, 0f, 0f)
        compositionOutput.reset()
    }
}
