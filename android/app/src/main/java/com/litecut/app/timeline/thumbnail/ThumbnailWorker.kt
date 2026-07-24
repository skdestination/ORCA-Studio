package com.litecut.app.timeline.thumbnail

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ThumbnailWorker(
    private val extractor: ThumbnailExtractor,
    private val cache: ThumbnailCache,
    private val isRequestValid: (ThumbnailRequest) -> Boolean
) {
    private val queue = PriorityBlockingQueue<ThumbnailRequest>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // A thread pool with a single worker or small core pool to prevent CPU contention with playback / UI thread
    private val executor = ThreadPoolExecutor(
        1, 2, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    )

    fun submit(request: ThumbnailRequest) {
        // Prune duplicate pending requests
        synchronized(queue) {
            val exists = queue.any { it.key == request.key }
            if (exists) return
            
            queue.add(request)
        }
        
        executor.execute {
            processNextRequest()
        }
    }

    private fun processNextRequest() {
        val request = synchronized(queue) {
            if (queue.isEmpty()) return
            queue.poll()
        } ?: return

        // 1. Check if the request is still valid/visible (fast-scrolling cancellation!)
        if (!isRequestValid(request)) {
            return
        }

        // 2. Query cache (L1 or L2) first
        var bitmap = cache.get(request.key, request.width, request.height)

        if (bitmap == null) {
            // 3. Extract the frame (heavy decoding happens on worker thread)
            bitmap = extractor.extract(
                src = request.src,
                isVideo = request.src.endsWith(".mp4") || request.src.endsWith(".mkv") || request.src.contains("video"), // Safe check
                timeOffsetSeconds = request.timeOffsetSeconds,
                targetWidth = request.width,
                targetHeight = request.height
            )

            if (bitmap != null) {
                // Save to cache
                cache.put(request.key, bitmap)
            }
        }

        // 4. Callback on Main Thread
        if (bitmap != null) {
            val finalBitmap = bitmap
            mainHandler.post {
                if (isRequestValid(request)) {
                    request.onComplete(finalBitmap)
                }
            }
        }
    }

    fun clearPendingRequests() {
        synchronized(queue) {
            queue.clear()
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
