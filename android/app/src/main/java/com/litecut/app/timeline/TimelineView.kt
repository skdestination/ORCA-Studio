package com.litecut.app.timeline

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val engine = TimelineEngine.getInstance()
    private val viewport = Viewport()
    private val renderer = TimelineRenderer()
    private val gestureHandler = TimelineGestureHandler(context, this, engine, viewport, renderer)

    val draggedClipId: String? get() = gestureHandler.draggedClipId
    val dragStartLeft: Double get() = gestureHandler.dragStartLeft
    val dragStartLayerId: String? get() = gestureHandler.dragStartLayerId
    val isDragging: Boolean get() = gestureHandler.touchMode == TimelineGestureHandler.TouchMode.DRAGGING

    var touchX: Float = 0f
    var touchY: Float = 0f
    val isReturning: Boolean get() = gestureHandler.isReturning
    val tempLayerAlpha: Float get() = gestureHandler.tempLayerAlpha
    val temporaryLayerId: String? get() = gestureHandler.temporaryLayerId
    val hoveredLayerId: String? get() = gestureHandler.hoveredLayerId
    val proposedLeftSeconds: Double get() = gestureHandler.proposedLeftSeconds
    val isCollision: Boolean get() = gestureHandler.isCollision

    val isMarqueeActive: Boolean get() = gestureHandler.isMarqueeActive
    val marqueeStartX: Float get() = gestureHandler.marqueeStartX
    val marqueeStartY: Float get() = gestureHandler.marqueeStartY
    val marqueeCurrentX: Float get() = gestureHandler.marqueeCurrentX
    val marqueeCurrentY: Float get() = gestureHandler.marqueeCurrentY

    init {
        // Enforce hardware acceleration layer for high-performance canvas rendering
        setLayerType(LAYER_TYPE_HARDWARE, null)
        com.litecut.app.timeline.thumbnail.ThumbnailEngine.getInstance(context).registerViewport(viewport)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewport.updateSize(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureHandler.onTouchEvent(event)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (gestureHandler.computeScrollOffset()) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Gather all clips for viewport awareness and prefetching updates
        val clips = engine.getAllClips()
        com.litecut.app.timeline.audio.WaveformEngine.getInstance(context)
            .updateViewportState(clips, viewport, engine.pixelsPerSecond)

        renderer.draw(canvas, engine, viewport, this)
    }

    /**
     * Set a custom zoom level. Automatically scales pixels per second and redraws.
     */
    fun setZoom(zoom: Double) {
        engine.zoomLevel = zoom.coerceIn(viewport.minZoom, viewport.maxZoom)
        engine.pixelsPerSecond = engine.zoomLevel * 100.0
        invalidate()
    }

    /**
     * Manually scroll to a horizontal pixel offset.
     */
    fun setScrollX(scroll: Double) {
        viewport.scrollX = scroll.coerceAtLeast(0.0)
        invalidate()
    }

    /**
     * Manually scroll to a vertical pixel offset.
     */
    fun setScrollY(scroll: Double) {
        viewport.scrollY = scroll.coerceAtLeast(0.0)
        invalidate()
    }

    /**
     * Get the current viewport details for external systems synchronization.
     */
    fun getViewport(): Viewport = viewport

    /**
     * Helper to add mock tracks and clips for testing the native timeline layout directly if needed.
     */
    fun setupDefaultMockTracks() {
        if (engine.getAllLayers().isEmpty()) {
            val track1 = engine.createLayer("layer_video_1", 100, "Main Video")
            val track2 = engine.createLayer("layer_video_2", 200, "Overlay Video")
            val track3 = engine.createLayer("layer_audio_1", 50, "Background Music")

            engine.addClipInternal(
                Clip("clip_1", track1.id, ClipType.VIDEO, "file:///path1", "Aesthetic Intro", 0.0, 15.0, 0.0)
            )
            engine.addClipInternal(
                Clip("clip_2", track1.id, ClipType.VIDEO, "file:///path2", "Cinematic Drone", 18.0, 32.0, 0.0)
            )
            engine.addClipInternal(
                Clip("clip_3", track2.id, ClipType.IMAGE, "file:///path3", "Watermark Overlay", 5.0, 25.0, 0.0)
            )
            engine.addClipInternal(
                Clip("clip_4", track3.id, ClipType.AUDIO, "file:///path4", "Lofi Sunset Beats", 2.0, 50.0, 0.0)
            )
        }
        invalidate()
    }
}
