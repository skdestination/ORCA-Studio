package com.litecut.app.timeline

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.widget.OverScroller
import kotlin.math.max
import kotlin.math.min

class TimelineGestureHandler(
    private val context: Context,
    private val view: TimelineView,
    private val engine: TimelineEngine,
    private val viewport: Viewport,
    private val renderer: TimelineRenderer
) : GestureDetector.SimpleOnGestureListener(), ScaleGestureDetector.OnScaleGestureListener {

    private val gestureDetector = GestureDetector(context, this).apply {
        setOnDoubleTapListener(this@TimelineGestureHandler)
    }
    private val scaleGestureDetector = ScaleGestureDetector(context, this)
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null

    enum class TouchMode {
        NONE, SCRUBBING, DRAGGING, SCROLLING
    }

    var touchMode = TouchMode.NONE
    var draggedClipId: String? = null
    var dragStartLeft = 0.0
    var dragStartLayerId: String? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Drag hold mechanics
    private val dragHoldHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isPendingDrag = false
    private var pendingClip: Clip? = null
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = 15f

    // Returning animation state
    var isReturning = false

    // Temporary Layer tracking
    var temporaryLayerId: String? = null
    var tempLayerAlpha: Float = 0f
    private var tempLayerAnimator: android.animation.ValueAnimator? = null

    // Real-time hover tracking
    var hoveredLayerId: String? = null
    var proposedLeftSeconds: Double = 0.0
    var isCollision: Boolean = false

    // Auto scroll mechanics
    private val autoScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (touchMode == TouchMode.DRAGGING && !isReturning) {
                val scrolled = checkAndPerformAutoScroll()
                if (scrolled) {
                    updateDragPreview(lastTouchX, lastTouchY)
                    view.invalidate()
                }
                autoScrollHandler.postDelayed(this, 16)
            }
        }
    }

    private val dragHoldRunnable = Runnable {
        if (isPendingDrag && pendingClip != null) {
            isPendingDrag = false
            
            // Trigger light haptic feedback!
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            
            // Start DRAGGING mode!
            touchMode = TouchMode.DRAGGING
            draggedClipId = pendingClip!!.id
            dragStartLeft = pendingClip!!.leftSeconds
            dragStartLayerId = pendingClip!!.layerId
            
            // Initial touch coordinate
            view.touchX = downX
            view.touchY = downY
            
            // Select clip if not selected
            if (!engine.selectedClipIds.contains(pendingClip!!.id)) {
                engine.selectedClipIds.clear()
                engine.selectedClipIds.add(pendingClip!!.id)
            }
            
            updateDragPreview(downX, downY)
            
            // Start auto-scroll checker
            autoScrollHandler.removeCallbacks(autoScrollRunnable)
            autoScrollHandler.post(autoScrollRunnable)
            
            view.invalidate()
        }
    }

    /**
     * Entry point for dispatching raw motion events down to standard trackers.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        scaleGestureDetector.onTouchEvent(event)
        if (scaleGestureDetector.isInProgress) {
            scroller.forceFinished(true)
            return true
        }

        gestureDetector.onTouchEvent(event)

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastTouchX = x
                lastTouchY = y
                downX = x
                downY = y

                if (isReturning) {
                    return true
                }

                if (y < renderer.headerHeight) {
                    touchMode = TouchMode.SCRUBBING
                    scrubPlayhead(x)
                } else {
                    val hitClip = hitTestClip(x, y)
                    if (hitClip != null) {
                        isPendingDrag = true
                        pendingClip = hitClip
                        dragHoldHandler.removeCallbacks(dragHoldRunnable)
                        dragHoldHandler.postDelayed(dragHoldRunnable, 200)
                    } else {
                        touchMode = TouchMode.SCROLLING
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                lastTouchX = x
                lastTouchY = y

                if (isReturning) {
                    return true
                }

                if (isPendingDrag) {
                    val dx = x - downX
                    val dy = y - downY
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        isPendingDrag = false
                        dragHoldHandler.removeCallbacks(dragHoldRunnable)
                        touchMode = TouchMode.SCROLLING
                    }
                }

                when (touchMode) {
                    TouchMode.SCRUBBING -> {
                        scrubPlayhead(x)
                    }
                    TouchMode.DRAGGING -> {
                        view.touchX = x
                        view.touchY = y
                        updateDragPreview(x, y)
                        view.invalidate()
                    }
                    TouchMode.SCROLLING -> {
                        val dx = lastTouchX - x
                        val dy = lastTouchY - y
                        viewport.handleScroll(dx, dy)
                        clampViewportScroll()
                        lastTouchX = x
                        lastTouchY = y
                        view.invalidate()
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragHoldHandler.removeCallbacks(dragHoldRunnable)
                autoScrollHandler.removeCallbacks(autoScrollRunnable)

                if (isReturning) {
                    return true
                }

                if (isPendingDrag) {
                    isPendingDrag = false
                    view.invalidate()
                } else if (touchMode == TouchMode.DRAGGING && draggedClipId != null) {
                    val clipId = draggedClipId!!
                    val clip = engine.getClip(clipId)
                    
                    if (clip != null) {
                        val destLayerId = hoveredLayerId
                        val destLeft = proposedLeftSeconds
                        
                        if (destLayerId != null && !isCollision) {
                            var finalLayerId = destLayerId
                            
                            if (finalLayerId == temporaryLayerId) {
                                val newPermanentId = "layer_" + java.util.UUID.randomUUID().toString()
                                val tempLayer = engine.getAllLayers().find { it.id == finalLayerId }
                                if (tempLayer != null) {
                                    engine.createLayer(newPermanentId, tempLayer.order, "Track ${tempLayer.order + 1}")
                                }
                                finalLayerId = newPermanentId
                                
                                tempLayerAnimator?.cancel()
                                engine.deleteLayer(temporaryLayerId!!)
                                temporaryLayerId = null
                            } else {
                                removeTemporaryLayer()
                            }
                            
                            val deltaSeconds = destLeft - dragStartLeft
                            val cmd = MoveCommand(listOf(clip.id), deltaSeconds, finalLayerId, dragStartLayerId!!)
                            engine.executeCommand(cmd)
                            
                            touchMode = TouchMode.NONE
                            draggedClipId = null
                            hoveredLayerId = null
                            view.invalidate()
                        } else {
                            removeTemporaryLayer()
                            
                            animateReturnToOriginal {
                                touchMode = TouchMode.NONE
                                draggedClipId = null
                                hoveredLayerId = null
                                view.invalidate()
                            }
                        }
                    } else {
                        removeTemporaryLayer()
                        touchMode = TouchMode.NONE
                        draggedClipId = null
                        hoveredLayerId = null
                        view.invalidate()
                    }
                } else if (touchMode == TouchMode.SCROLLING) {
                    velocityTracker?.let { tracker ->
                        tracker.computeCurrentVelocity(1000)
                        val xVelocity = tracker.xVelocity
                        val yVelocity = tracker.yVelocity

                        val totalDuration = engine.getTotalDurationSeconds()
                        val maxScrollX = max(0.0, totalDuration * engine.pixelsPerSecond - viewport.width + 200.0).toInt()
                        
                        val totalTracksHeight = engine.getAllLayers().size * (renderer.trackHeight + renderer.trackSpacing)
                        val maxScrollY = max(0.0, totalTracksHeight - viewport.height + renderer.headerHeight + 50.0).toInt()

                        scroller.fling(
                            viewport.scrollX.toInt(), viewport.scrollY.toInt(),
                            -xVelocity.toInt(), -yVelocity.toInt(),
                            0, maxScrollX,
                            0, maxScrollY,
                            50, 50
                        )
                        view.postInvalidateOnAnimation()
                    }
                }

                touchMode = TouchMode.NONE
                velocityTracker?.recycle()
                velocityTracker = null
                view.invalidate()
            }
        }
        return true
    }

    private fun updateDragPreview(x: Float, y: Float) {
        val clipId = draggedClipId ?: return
        val clip = engine.getClip(clipId) ?: return
        val pps = engine.pixelsPerSecond
        val ghostWidth = (clip.durationSeconds * pps).toFloat()
        
        val ghostLeftX = x - ghostWidth / 2f
        val timelineLeftPixel = ghostLeftX + viewport.scrollX
        proposedLeftSeconds = max(0.0, timelineLeftPixel / pps)
        
        val sortedLayers = engine.getAllLayers().sortedBy { it.order }
        val permanentLayers = sortedLayers.filter { !it.id.startsWith("temp_layer_") }
        
        val relativeY = y + viewport.scrollY - renderer.headerHeight
        val lastPermanentBottomY = permanentLayers.size * (renderer.trackHeight + renderer.trackSpacing) - renderer.trackSpacing
        
        if (relativeY > lastPermanentBottomY) {
            createTemporaryLayerIfNeeded()
            hoveredLayerId = temporaryLayerId
        } else {
            removeTemporaryLayer()
            
            val layerIndex = (relativeY / (renderer.trackHeight + renderer.trackSpacing)).toInt()
            val targetIndex = max(0, min(permanentLayers.size - 1, layerIndex))
            hoveredLayerId = permanentLayers[targetIndex].id
        }
        
        hoveredLayerId?.let { targetLayerId ->
            isCollision = checkCollision(clipId, proposedLeftSeconds, clip.durationSeconds, targetLayerId)
        }
    }

    private fun checkCollision(clipId: String, leftSeconds: Double, durationSeconds: Double, targetLayerId: String): Boolean {
        val endSeconds = leftSeconds + durationSeconds
        for (other in engine.getAllClips()) {
            if (other.id == clipId) continue
            if (other.layerId != targetLayerId) continue
            val otherEnd = other.leftSeconds + other.durationSeconds
            if (leftSeconds < otherEnd && endSeconds > other.leftSeconds) {
                return true
            }
        }
        return false
    }

    private fun createTemporaryLayerIfNeeded() {
        if (temporaryLayerId != null) return
        
        val sortedLayers = engine.getAllLayers().sortedBy { it.order }
        val nextOrder = if (sortedLayers.isEmpty()) 0 else sortedLayers.last().order + 1
        val tempId = "temp_layer_" + java.util.UUID.randomUUID().toString()
        
        engine.createLayer(tempId, nextOrder, "New Track")
        temporaryLayerId = tempId
        
        tempLayerAlpha = 0f
        tempLayerAnimator?.cancel()
        tempLayerAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            addUpdateListener { animator ->
                tempLayerAlpha = animator.animatedValue as Float
                view.invalidate()
            }
            start()
        }
    }

    private fun removeTemporaryLayer() {
        val tempId = temporaryLayerId ?: return
        temporaryLayerId = null
        
        tempLayerAnimator?.cancel()
        tempLayerAnimator = android.animation.ValueAnimator.ofFloat(tempLayerAlpha, 0f).apply {
            duration = 180
            addUpdateListener { animator ->
                tempLayerAlpha = animator.animatedValue as Float
                view.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    engine.deleteLayer(tempId)
                    view.invalidate()
                }
            })
            start()
        }
    }

    private fun checkAndPerformAutoScroll(): Boolean {
        val x = lastTouchX
        val width = viewport.width.toFloat()
        val edgeWidth = 150f
        val maxScrollSpeed = 25f
        
        if (x < edgeWidth && x >= 0) {
            val ratio = (edgeWidth - x) / edgeWidth
            val scrollAmount = ratio * maxScrollSpeed
            val oldScrollX = viewport.scrollX
            viewport.scrollX = max(0.0, viewport.scrollX - scrollAmount)
            return viewport.scrollX != oldScrollX
        } else if (x > width - edgeWidth && x <= width) {
            val ratio = (x - (width - edgeWidth)) / edgeWidth
            val scrollAmount = ratio * maxScrollSpeed
            val oldScrollX = viewport.scrollX
            val totalDuration = engine.getTotalDurationSeconds()
            val maxScrollX = max(0.0, totalDuration * engine.pixelsPerSecond - viewport.width + 200.0)
            viewport.scrollX = min(maxScrollX, viewport.scrollX + scrollAmount)
            return viewport.scrollX != oldScrollX
        }
        return false
    }

    private fun animateReturnToOriginal(onComplete: () -> Unit) {
        val startX = view.touchX
        val startY = view.touchY
        
        val pps = engine.pixelsPerSecond
        val sortedLayers = engine.getAllLayers().sortedBy { it.order }
        val origLayerIndex = sortedLayers.indexOfFirst { it.id == dragStartLayerId }
        val origY = if (origLayerIndex != -1) {
            renderer.headerHeight + origLayerIndex * (renderer.trackHeight + renderer.trackSpacing) - viewport.scrollY.toFloat() + renderer.trackHeight / 2f
        } else {
            startY
        }
        val origX = (dragStartLeft * pps - viewport.scrollX).toFloat() + (engine.getClip(draggedClipId!!)!!.durationSeconds * pps).toFloat() / 2f
        
        isReturning = true
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            setInterpolator(android.view.animation.DecelerateInterpolator())
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                view.touchX = startX + (origX - startX) * fraction
                view.touchY = startY + (origY - startY) * fraction
                view.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isReturning = false
                    onComplete()
                }
            })
        }
        animator.start()
    }

    private fun clampViewportScroll() {
        val totalDuration = engine.getTotalDurationSeconds()
        val maxScrollX = max(0.0, totalDuration * engine.pixelsPerSecond - viewport.width + 200.0)
        
        val totalTracksHeight = engine.getAllLayers().size * (renderer.trackHeight + renderer.trackSpacing)
        val maxScrollY = max(0.0, totalTracksHeight - viewport.height + renderer.headerHeight + 50.0)

        viewport.scrollX = viewport.scrollX.coerceIn(0.0, maxScrollX)
        viewport.scrollY = viewport.scrollY.coerceIn(0.0, maxScrollY)
    }

    fun computeScrollOffset(): Boolean {
        if (scroller.computeScrollOffset()) {
            viewport.scrollX = scroller.currX.toDouble()
            viewport.scrollY = scroller.currY.toDouble()
            clampViewportScroll()
            return true
        }
        return false
    }

    private fun scrubPlayhead(x: Float) {
        val pps = engine.pixelsPerSecond
        val playheadTime = (x + viewport.scrollX) / pps
        engine.currentTime = max(0.0, playheadTime)
        view.invalidate()
    }

    private fun hitTestClip(x: Float, y: Float): Clip? {
        val pps = engine.pixelsPerSecond
        val sortedLayers = engine.getAllLayers().sortedBy { it.order }

        for (clip in engine.getAllClips()) {
            val layerIndex = sortedLayers.indexOfFirst { it.id == clip.layerId }
            if (layerIndex == -1) continue

            val clipY = renderer.headerHeight + layerIndex * (renderer.trackHeight + renderer.trackSpacing) - viewport.scrollY.toFloat()
            val clipBottom = clipY + renderer.trackHeight

            if (y >= clipY && y <= clipBottom) {
                val clipLeft = (clip.leftSeconds * pps - viewport.scrollX).toFloat()
                val clipRight = (clipLeft + clip.durationSeconds * pps).toFloat()
                if (x >= clipLeft && x <= clipRight) {
                    return clip
                }
            }
        }
        return null
    }

    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (touchMode == TouchMode.SCROLLING) {
            viewport.handleScroll(distanceX, distanceY)
            clampViewportScroll()
            view.invalidate()
            return true
        }
        return false
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y

        if (y < renderer.headerHeight) {
            scrubPlayhead(x)
            return true
        }

        val clickedClip = hitTestClip(x, y)
        if (clickedClip != null) {
            engine.selectedClipIds.clear()
            engine.selectedClipIds.add(clickedClip.id)
        } else {
            engine.selectedClipIds.clear()
        }
        view.invalidate()
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        val clickedClip = hitTestClip(x, y)
        if (clickedClip != null) {
            val pps = engine.pixelsPerSecond
            val targetTime = (x + viewport.scrollX) / pps
            if (targetTime > clickedClip.leftSeconds && targetTime < (clickedClip.leftSeconds + clickedClip.durationSeconds)) {
                val command = SplitCommand(clickedClip.id, targetTime, java.util.UUID.randomUUID().toString())
                engine.executeCommand(command)
                view.invalidate()
                return true
            }
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val oldPps = engine.pixelsPerSecond
        val scaleFactor = detector.scaleFactor

        val oldZoom = engine.zoomLevel
        val newZoom = (oldZoom * scaleFactor).coerceIn(viewport.minZoom, viewport.maxZoom)
        engine.zoomLevel = newZoom
        engine.pixelsPerSecond = newZoom * 100.0

        val newPps = engine.pixelsPerSecond
        val focusX = detector.focusX

        val timeAtFocus = (focusX + viewport.scrollX) / oldPps
        viewport.scrollX = max(0.0, timeAtFocus * newPps - focusX)
        clampViewportScroll()

        view.invalidate()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
}
