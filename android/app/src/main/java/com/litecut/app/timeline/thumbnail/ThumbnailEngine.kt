package com.litecut.app.timeline.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.litecut.app.timeline.Clip
import com.litecut.app.timeline.TimelineEngine
import com.litecut.app.timeline.Viewport
import kotlin.math.abs

class ThumbnailEngine private constructor(val context: Context) {

    val bitmapPool = BitmapPool(context)
    val cache = ThumbnailCache(context, bitmapPool)
    private val extractor = ThumbnailExtractor(context, bitmapPool)

    // The validation lambda used by the worker
    private val requestValidator: (ThumbnailRequest) -> Boolean = { req ->
        isRequestVisibleAndValid(req)
    }

    private val worker = ThumbnailWorker(extractor, cache, requestValidator)

    // Reference to current viewport to handle visibility checking and prioritization
    private var activeViewport: Viewport? = null

    companion object {
        @Volatile
        private var instance: ThumbnailEngine? = null

        fun getInstance(context: Context): ThumbnailEngine {
            return instance ?: synchronized(this) {
                instance ?: ThumbnailEngine(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): ThumbnailEngine {
            return instance ?: throw IllegalStateException("ThumbnailEngine has not been initialized with Context.")
        }
    }

    /**
     * Registers the current active viewport to allow the engine to check visibility.
     */
    fun registerViewport(viewport: Viewport) {
        activeViewport = viewport
    }

    /**
     * Main entry point to request a thumbnail.
     * Checks caches synchronously on the caller thread, and offloads extraction asynchronously if needed.
     */
    fun requestThumbnail(
        clip: Clip,
        timeOffsetSeconds: Double,
        width: Int,
        height: Int,
        onComplete: (Bitmap) -> Unit
    ) {
        val key = "${clip.id}@$timeOffsetSeconds"

        // 1. Synchronous check in L1 Memory/L2 Disk Cache
        val cached = cache.get(key, width, height)
        if (cached != null) {
            onComplete(cached)
            return
        }

        // 2. Not cached - build an asynchronous request
        val request = ThumbnailRequest(
            clipId = clip.id,
            src = clip.src,
            timeOffsetSeconds = timeOffsetSeconds,
            width = width,
            height = height,
            onComplete = onComplete
        )

        // 3. Prioritize based on center proximity
        request.priority = calculateRequestPriority(request, clip)

        // 4. Submit to queue
        worker.submit(request)
    }

    /**
     * Clears all pending workers and releases cached bitmaps when project is closed or switched.
     */
    fun clear() {
        worker.clearPendingRequests()
        cache.clear()
        bitmapPool.clear()
    }

    fun shutdown() {
        worker.shutdown()
        clear()
    }

    /**
     * Checks if a specific requested thumbnail is still valid and visible in the viewport.
     */
    private fun isRequestVisibleAndValid(request: ThumbnailRequest): Boolean {
        val engine = TimelineEngine.getInstance()
        val clip = engine.getClip(request.clipId) ?: return false // Clip was deleted

        val viewport = activeViewport ?: return true // If no viewport registered, fallback to true

        val pps = engine.pixelsPerSecond
        val thumbTimelineSeconds = clip.leftSeconds + (request.timeOffsetSeconds - clip.trimStartSeconds) / clip.speed
        val thumbPixelX = thumbTimelineSeconds * pps

        // Add horizontal padding to lookahead load next frames pre-emptively
        val lookaheadPadding = viewport.width * 0.5 
        val leftBound = viewport.scrollX - lookaheadPadding
        val rightBound = viewport.scrollX + viewport.width + lookaheadPadding

        val thumbWidthPx = request.width
        val isVisible = (thumbPixelX + thumbWidthPx >= leftBound) && (thumbPixelX <= rightBound)

        return isVisible
    }

    /**
     * Compiles a priority value where high priorities represent items closest to the viewport's center.
     */
    private fun calculateRequestPriority(request: ThumbnailRequest, clip: Clip): Int {
        val viewport = activeViewport ?: return 0
        val engine = TimelineEngine.getInstance()
        val pps = engine.pixelsPerSecond

        val thumbTimelineSeconds = clip.leftSeconds + (request.timeOffsetSeconds - clip.trimStartSeconds) / clip.speed
        val thumbPixelX = thumbTimelineSeconds * pps

        val viewportCenterX = viewport.scrollX + viewport.width / 2.0
        val distance = abs(thumbPixelX - viewportCenterX)

        // Map short distances to very high priorities
        val maxDist = viewport.width * 2.0
        val score = (maxDist - distance).coerceAtLeast(0.0)
        return score.toInt()
    }
}
