package com.litecut.app.timeline

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Path
import com.litecut.app.timeline.audio.WaveformEngine
import com.litecut.app.timeline.audio.WaveformRenderer

class TimelineRenderer {
    private val waveformRenderer = WaveformRenderer()
    private val bgPaint = Paint().apply { style = Paint.Style.FILL }
    private val trackBgPaint = Paint().apply { style = Paint.Style.FILL }
    
    // Clip paints
    private val clipVideoPaint = Paint().apply { style = Paint.Style.FILL }
    private val clipAudioPaint = Paint().apply { style = Paint.Style.FILL }
    private val clipTextPaint = Paint().apply { style = Paint.Style.FILL }
    private val clipImagePaint = Paint().apply { style = Paint.Style.FILL }
    private val clipBorderPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    
    // Selection and UI paints
    private val selectionBorderPaint = Paint().apply { style = Paint.Style.STROKE }
    private val textPaint = Paint().apply { isAntiAlias = true }
    private val rulerBgPaint = Paint().apply { style = Paint.Style.FILL }
    private val rulerTickPaint = Paint().apply { strokeWidth = 2f; style = Paint.Style.STROKE }
    private val rulerTextPaint = Paint().apply { isAntiAlias = true }
    
    // Playhead paints
    private val playheadLinePaint = Paint().apply { style = Paint.Style.STROKE }
    private val playheadHeadPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    // Reusable geometry to prevent runtime object allocations in onDraw()
    private val tempRect = RectF()
    private val tempTextBounds = Rect()
    private val playheadPath = Path()
    private val clipPath = Path()
    private val slotRect = RectF()

    // Dimensions linked dynamically to TimelineTheme
    val headerHeight: Float get() = TimelineTheme.headerHeight
    val trackHeight: Float get() = TimelineTheme.trackHeight
    val trackSpacing: Float get() = TimelineTheme.trackSpacing
    val clipCornerRadius: Float get() = TimelineTheme.clipCornerRadius

    private fun syncPaintsWithTheme() {
        bgPaint.color = TimelineTheme.backgroundColor
        trackBgPaint.color = TimelineTheme.trackBackgroundColor
        
        clipVideoPaint.color = TimelineTheme.clipVideoColor
        clipAudioPaint.color = TimelineTheme.clipAudioColor
        clipTextPaint.color = TimelineTheme.clipTextColor
        clipImagePaint.color = TimelineTheme.clipImageColor
        clipBorderPaint.color = TimelineTheme.clipBorderColor
        
        selectionBorderPaint.color = TimelineTheme.selectionBorderColor
        selectionBorderPaint.strokeWidth = TimelineTheme.selectionBorderWidth
        
        textPaint.color = TimelineTheme.clipLabelColor
        textPaint.textSize = TimelineTheme.clipLabelSize
        
        rulerBgPaint.color = TimelineTheme.headerBackgroundColor
        rulerTickPaint.color = TimelineTheme.rulerTickColor
        rulerTextPaint.color = TimelineTheme.rulerTextColor
        rulerTextPaint.textSize = TimelineTheme.rulerTextSize
        
        playheadLinePaint.color = TimelineTheme.playheadLineColor
        playheadLinePaint.strokeWidth = TimelineTheme.playheadLineWidth
        playheadHeadPaint.color = TimelineTheme.playheadHeadColor
    }

    fun draw(canvas: Canvas, engine: TimelineEngine, viewport: Viewport, view: TimelineView) {
        // Synchronize paints to capture any changes in the centralized theme
        syncPaintsWithTheme()

        val pps = engine.pixelsPerSecond
        val thumbnailEngine = com.litecut.app.timeline.thumbnail.ThumbnailEngine.getInstance(view.context)
        
        // 1. Draw global background
        canvas.drawColor(bgPaint.color)

        // Sort layers to establish proper draw order
        val sortedLayers = engine.getAllLayers().sortedBy { it.order }

        // 2. Draw tracks
        var currentY = headerHeight - viewport.scrollY.toFloat()
        for (layer in sortedLayers) {
            val trackBottom = currentY + trackHeight
            
            // Vertical Virtualization check
            if (trackBottom >= headerHeight && currentY <= viewport.height) {
                // Draw track lane background
                tempRect.set(0f, currentY, viewport.width.toFloat(), trackBottom)
                if (layer.id.startsWith("temp_layer_")) {
                    val originalTrackBgAlpha = trackBgPaint.alpha
                    trackBgPaint.alpha = (view.tempLayerAlpha * originalTrackBgAlpha).toInt()
                    canvas.drawRect(tempRect, trackBgPaint)
                    trackBgPaint.alpha = originalTrackBgAlpha
                } else {
                    canvas.drawRect(tempRect, trackBgPaint)
                }
            }
            
            currentY += trackHeight + trackSpacing
        }

        // 3. Draw clips
        for (clip in engine.getAllClips()) {
            // Find layer vertical position
            val layerIndex = sortedLayers.indexOfFirst { it.id == clip.layerId }
            if (layerIndex == -1) continue

            val clipY = headerHeight + layerIndex * (trackHeight + trackSpacing) - viewport.scrollY.toFloat()
            val clipBottom = clipY + trackHeight

            // Vertical Virtualization check
            if (clipBottom < headerHeight || clipY > viewport.height) {
                continue
            }

            // Horizontal Virtualization check
            val leftX = (clip.leftSeconds * pps - viewport.scrollX).toFloat()
            val rightX = (leftX + clip.durationSeconds * pps).toFloat()
            
            if (rightX < 0f || leftX > viewport.width) {
                continue
            }

            // Select color based on clip type
            val clipPaint = when (clip.type) {
                ClipType.VIDEO -> clipVideoPaint
                ClipType.AUDIO -> {
                    val wEngine = WaveformEngine.getInstance(view.context)
                    val trackType = wEngine.getAudioTrackType(clip)
                    val trackColor = wEngine.getTrackColor(trackType)
                    // Semi-transparent deep dark background card blended with track color
                    val blendedColor = (trackColor and 0x00FFFFFF) or (0x1F shl 24)
                    clipAudioPaint.apply { color = blendedColor }
                }
                ClipType.TEXT -> clipTextPaint
                ClipType.IMAGE -> clipImagePaint
            }

            val isBeingDragged = view.isDragging && clip.id == view.draggedClipId
            
            val originalClipPaintAlpha = clipPaint.alpha
            val originalClipBorderAlpha = clipBorderPaint.alpha
            val originalTextAlpha = textPaint.alpha
            val originalSelectionBorderAlpha = selectionBorderPaint.alpha

            if (isBeingDragged) {
                // Keep the original container at its original position, but make it semi-transparent!
                clipPaint.alpha = 75
                clipBorderPaint.alpha = 90
                textPaint.alpha = 75
                selectionBorderPaint.alpha = 90
            }

            // Draw clip background card
            tempRect.set(leftX, clipY + 4f, rightX, clipBottom - 4f)
            canvas.drawRoundRect(tempRect, clipCornerRadius, clipCornerRadius, clipPaint)

            // Draw audio waveforms for AUDIO clips, or filmstrips for VIDEO/IMAGE clips
            if (clip.type == ClipType.AUDIO) {
                val wEngine = WaveformEngine.getInstance(view.context)
                val layerIndex = sortedLayers.indexOfFirst { it.id == clip.layerId }
                val layer = if (layerIndex != -1) sortedLayers[layerIndex] else null
                val isMuted = (layer?.isMuted == true) || 
                              (clip.additionalProperties["mute"] as? Boolean == true) || 
                              (clip.additionalProperties["isMuted"] as? Boolean == true)

                waveformRenderer.drawWaveform(
                    canvas = canvas,
                    clip = clip,
                    clipRect = tempRect,
                    viewport = viewport,
                    pps = pps,
                    engine = wEngine,
                    isSelected = engine.selectedClipIds.contains(clip.id),
                    isMuted = isMuted,
                    view = view
                )
            }

            // Draw Filmstrip thumbnails if VIDEO or IMAGE clip
            if (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE) {
                val thumbHeight = trackHeight - 8f
                val thumbWidth = thumbHeight * 16f / 9f // 16:9 Aspect Ratio
                val clipWidth = clip.durationSeconds * pps

                if (clipWidth > 0f) {
                    val numThumbs = Math.ceil(clipWidth / thumbWidth).toInt()

                    canvas.save()
                    clipPath.reset()
                    clipPath.addRoundRect(tempRect, clipCornerRadius, clipCornerRadius, Path.Direction.CW)
                    canvas.clipPath(clipPath)

                    for (i in 0 until numThumbs) {
                        val xOffset = i * thumbWidth
                        val thumbLeftTimelineX = clip.leftSeconds * pps + xOffset
                        val thumbLeftX = (thumbLeftTimelineX - viewport.scrollX).toFloat()
                        val thumbRightX = thumbLeftX + thumbWidth

                        // Horizontal Virtualization Check
                        if (thumbRightX < 0f || thumbLeftX > viewport.width) {
                            continue
                        }

                        // Calculate appropriate source time offset
                        val timeWithinClipSeconds = (xOffset / pps) * clip.speed
                        val timeOffsetSeconds = clip.trimStartSeconds + timeWithinClipSeconds

                        val key = "${clip.id}@$timeOffsetSeconds"
                        val bitmap = thumbnailEngine.cache.get(key, thumbWidth.toInt(), thumbHeight.toInt())

                        slotRect.set(thumbLeftX, clipY + 4f, thumbRightX, clipBottom - 4f)

                        if (bitmap != null && !bitmap.isRecycled) {
                            canvas.drawBitmap(bitmap, null, slotRect, null)
                        } else {
                            // Asynchronously load frame and invalidate only this thumbnail slot
                            thumbnailEngine.requestThumbnail(
                                clip,
                                timeOffsetSeconds,
                                thumbWidth.toInt(),
                                thumbHeight.toInt()
                            ) { loadedBitmap ->
                                val dirtyLeft = thumbLeftX.toInt()
                                val dirtyTop = (clipY + 4f).toInt()
                                val dirtyRight = thumbRightX.toInt()
                                val dirtyBottom = (clipBottom - 4f).toInt()
                                view.postInvalidate(dirtyLeft, dirtyTop, dirtyRight, dirtyBottom)
                            }
                        }
                    }
                    canvas.restore()
                }
            }

            canvas.drawRoundRect(tempRect, clipCornerRadius, clipCornerRadius, clipBorderPaint)

            // Draw selection borders if highlighted
            if (engine.selectedClipIds.contains(clip.id)) {
                canvas.drawRoundRect(tempRect, clipCornerRadius, clipCornerRadius, selectionBorderPaint)
            }

            // Keyframe Polyline Slope Overlay
            val keyframeArray = clip.additionalProperties["keyframes"] as? org.json.JSONArray
            if (keyframeArray != null && keyframeArray.length() > 0) {
                val keyframes = ArrayList<Keyframe>()
                for (i in 0 until keyframeArray.length()) {
                    val kfObj = keyframeArray.optJSONObject(i) ?: continue
                    try {
                        keyframes.add(Keyframe.fromJSONObject(kfObj))
                    } catch (e: Exception) {}
                }
                
                if (keyframes.isNotEmpty()) {
                    keyframes.sortBy { it.timeOffset }
                    val curvePath = Path()
                    val clipHeight = trackHeight - 12f
                    val clipBottomMargin = clipBottom - 6f
                    
                    var isFirst = true
                    for (kf in keyframes) {
                        val kfX = leftX + (kf.timeOffset * pps).toFloat()
                        val normVal = (kf.value.coerceIn(0.0, 2.0) / 2.0).toFloat()
                        val kfY = clipBottomMargin - (normVal * clipHeight)
                        
                        if (isFirst) {
                            curvePath.moveTo(kfX, kfY)
                            isFirst = false
                        } else {
                            curvePath.lineTo(kfX, kfY)
                        }
                    }
                    
                    val slopeLinePaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        color = 0xFF818CF8.toInt()
                        isAntiAlias = true
                    }
                    canvas.drawPath(curvePath, slopeLinePaint)
                    
                    val diamondPaint = Paint().apply {
                        style = Paint.Style.FILL
                        color = 0xFFFFFFFF.toInt()
                        isAntiAlias = true
                    }
                    val diamondBorderPaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                        color = 0xFF6366F1.toInt()
                        isAntiAlias = true
                    }
                    val diamondPath = Path()
                    val kfRadius = 6f
                    
                    for (kf in keyframes) {
                        val kfX = leftX + (kf.timeOffset * pps).toFloat()
                        val normVal = (kf.value.coerceIn(0.0, 2.0) / 2.0).toFloat()
                        val kfY = clipBottomMargin - (normVal * clipHeight)
                        
                        diamondPath.reset()
                        diamondPath.moveTo(kfX, kfY - kfRadius)
                        diamondPath.lineTo(kfX + kfRadius, kfY)
                        diamondPath.lineTo(kfX, kfY + kfRadius)
                        diamondPath.lineTo(kfX - kfRadius, kfY)
                        diamondPath.close()
                        
                        canvas.drawPath(diamondPath, diamondPaint)
                        canvas.drawPath(diamondPath, diamondBorderPaint)
                    }
                }
            }

            // Draw clip name (ensure bounds checking for text)
            val clipName = clip.name ?: clip.type.name
            textPaint.getTextBounds(clipName, 0, clipName.length, tempTextBounds)
            val textWidth = tempTextBounds.width()
            val textHeight = tempTextBounds.height()
            
            val maxTextWidth = (rightX - leftX) - 24f
            if (maxTextWidth > 10f) {
                // Clip text vertically and horizontally to prevent overflows
                canvas.save()
                canvas.clipRect(leftX + 12f, clipY, rightX - 12f, clipBottom)
                
                val textX = leftX + 16f
                val textY = clipY + (trackHeight + textHeight) / 2f
                canvas.drawText(clipName, textX, textY, textPaint)
                
                canvas.restore()
            }

            if (isBeingDragged) {
                clipPaint.alpha = originalClipPaintAlpha
                clipBorderPaint.alpha = originalClipBorderAlpha
                textPaint.alpha = originalTextAlpha
                selectionBorderPaint.alpha = originalSelectionBorderAlpha
            }
        }

        // 3.5 Draw placement preview box
        if (view.isDragging && view.hoveredLayerId != null) {
            val clipId = view.draggedClipId
            val clip = clipId?.let { engine.getClip(it) }
            if (clip != null) {
                val hoveredLayerIndex = sortedLayers.indexOfFirst { it.id == view.hoveredLayerId }
                if (hoveredLayerIndex != -1) {
                    val previewY = headerHeight + hoveredLayerIndex * (trackHeight + trackSpacing) - viewport.scrollY.toFloat()
                    val previewBottom = previewY + trackHeight
                    
                    val previewLeftX = (view.proposedLeftSeconds * pps - viewport.scrollX).toFloat()
                    val previewRightX = (previewLeftX + clip.durationSeconds * pps).toFloat()
                    
                    val previewRect = RectF(previewLeftX, previewY + 4f, previewRightX, previewBottom - 4f)
                    
                    val isCol = view.isCollision
                    val previewPaint = Paint().apply {
                        style = Paint.Style.FILL_AND_STROKE
                        strokeWidth = 4f
                        color = if (isCol) 0x33FF0000.toInt() else 0x224ADE80.toInt()
                    }
                    canvas.drawRoundRect(previewRect, clipCornerRadius, clipCornerRadius, previewPaint)
                    
                    val previewBorderPaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 4f
                        color = if (isCol) 0xFFFF0000.toInt() else 0xFF4ADE80.toInt()
                    }
                    canvas.drawRoundRect(previewRect, clipCornerRadius, clipCornerRadius, previewBorderPaint)
                }
            }
        }

        // 3.6 Draw floating ghost container
        if (view.isDragging && view.draggedClipId != null) {
            val clip = engine.getClip(view.draggedClipId!!)
            if (clip != null) {
                val touchX = view.touchX
                val touchY = view.touchY
                
                val ghostWidth = (clip.durationSeconds * pps).toFloat()
                val ghostHeight = trackHeight
                
                val leftX = touchX - ghostWidth / 2f
                val rightX = touchX + ghostWidth / 2f
                val topY = touchY - ghostHeight / 2f
                val bottomY = touchY + ghostHeight / 2f
                
                // Shadow
                val shadowPaint = Paint().apply {
                    color = 0x66000000.toInt()
                    style = Paint.Style.FILL
                }
                val shadowRect = RectF(leftX + 4f, topY + 8f, rightX + 4f, bottomY + 8f)
                canvas.drawRoundRect(shadowRect, clipCornerRadius, clipCornerRadius, shadowPaint)
                
                // Adjust alphas for ghost card
                val originalClipVideoPaintAlpha = clipVideoPaint.alpha
                val originalClipAudioPaintAlpha = clipAudioPaint.alpha
                val originalClipTextPaintAlpha = clipTextPaint.alpha
                val originalClipImagePaintAlpha = clipImagePaint.alpha
                val originalClipBorderPaintAlpha = clipBorderPaint.alpha
                val originalTextPaintAlpha = textPaint.alpha
                
                val ghostAlpha = 180
                clipVideoPaint.alpha = ghostAlpha
                clipAudioPaint.alpha = ghostAlpha
                clipTextPaint.alpha = ghostAlpha
                clipImagePaint.alpha = ghostAlpha
                clipBorderPaint.alpha = ghostAlpha
                textPaint.alpha = ghostAlpha
                
                val clipPaint = when (clip.type) {
                    ClipType.VIDEO -> clipVideoPaint
                    ClipType.AUDIO -> {
                        val wEngine = WaveformEngine.getInstance(view.context)
                        val trackType = wEngine.getAudioTrackType(clip)
                        val trackColor = wEngine.getTrackColor(trackType)
                        val blendedColor = (trackColor and 0x00FFFFFF) or (0x1F shl 24)
                        clipAudioPaint.apply { color = blendedColor }
                    }
                    ClipType.TEXT -> clipTextPaint
                    ClipType.IMAGE -> clipImagePaint
                }
                
                val ghostRect = RectF(leftX, topY, rightX, bottomY)
                canvas.drawRoundRect(ghostRect, clipCornerRadius, clipCornerRadius, clipPaint)
                
                // Audio Waveform
                if (clip.type == ClipType.AUDIO) {
                    val wEngine = WaveformEngine.getInstance(view.context)
                    val layerIndex = sortedLayers.indexOfFirst { it.id == clip.layerId }
                    val layer = if (layerIndex != -1) sortedLayers[layerIndex] else null
                    val isMuted = (layer?.isMuted == true) || 
                                  (clip.additionalProperties["mute"] as? Boolean == true) || 
                                  (clip.additionalProperties["isMuted"] as? Boolean == true)

                    waveformRenderer.drawWaveform(
                        canvas = canvas,
                        clip = clip,
                        clipRect = ghostRect,
                        viewport = viewport,
                        pps = pps,
                        engine = wEngine,
                        isSelected = engine.selectedClipIds.contains(clip.id),
                        isMuted = isMuted,
                        view = view
                    )
                } else if (clip.type == ClipType.VIDEO || clip.type == ClipType.IMAGE) {
                    val thumbHeight = trackHeight - 8f
                    val thumbWidth = thumbHeight * 16f / 9f
                    if (ghostWidth > 0f) {
                        val numThumbs = Math.ceil(ghostWidth.toDouble() / thumbWidth).toInt()
                        
                        canvas.save()
                        clipPath.reset()
                        clipPath.addRoundRect(ghostRect, clipCornerRadius, clipCornerRadius, Path.Direction.CW)
                        canvas.clipPath(clipPath)
                        
                        for (i in 0 until numThumbs) {
                            val xOffset = i * thumbWidth
                            val thumbLeftX = leftX + xOffset
                            val thumbRightX = thumbLeftX + thumbWidth
                            
                            val timeWithinClipSeconds = (xOffset / pps) * clip.speed
                            val timeOffsetSeconds = clip.trimStartSeconds + timeWithinClipSeconds
                            
                            val key = "${clip.id}@$timeOffsetSeconds"
                            val bitmap = thumbnailEngine.cache.get(key, thumbWidth.toInt(), thumbHeight.toInt())
                            
                            slotRect.set(thumbLeftX, topY + 4f, thumbRightX, bottomY - 4f)
                            if (bitmap != null && !bitmap.isRecycled) {
                                canvas.drawBitmap(bitmap, null, slotRect, null)
                            }
                        }
                        canvas.restore()
                    }
                }
                
                // Border - turns red on collision!
                val borderPaintToUse = if (view.isCollision) {
                    Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 4f
                        color = 0xFFFF0000.toInt()
                    }
                } else {
                    clipBorderPaint
                }
                canvas.drawRoundRect(ghostRect, clipCornerRadius, clipCornerRadius, borderPaintToUse)
                
                // Selection Highlight
                if (engine.selectedClipIds.contains(clip.id)) {
                    canvas.drawRoundRect(ghostRect, clipCornerRadius, clipCornerRadius, selectionBorderPaint)
                }
                
                // Name
                val clipName = clip.name ?: clip.type.name
                textPaint.getTextBounds(clipName, 0, clipName.length, tempTextBounds)
                val textHeight = tempTextBounds.height()
                
                val maxTextWidth = (rightX - leftX) - 24f
                if (maxTextWidth > 10f) {
                    canvas.save()
                    canvas.clipRect(leftX + 12f, topY, rightX - 12f, bottomY)
                    
                    val textX = leftX + 16f
                    val textY = topY + (trackHeight + textHeight) / 2f
                    canvas.drawText(clipName, textX, textY, textPaint)
                    
                    canvas.restore()
                }
                
                // Restore alphas
                clipVideoPaint.alpha = originalClipVideoPaintAlpha
                clipAudioPaint.alpha = originalClipAudioPaintAlpha
                clipTextPaint.alpha = originalClipTextPaintAlpha
                clipImagePaint.alpha = originalClipImagePaintAlpha
                clipBorderPaint.alpha = originalClipBorderPaintAlpha
                textPaint.alpha = originalTextPaintAlpha
            }
        }

        // 4. Draw Ruler background over clips (covers top edge)
        tempRect.set(0f, 0f, viewport.width.toFloat(), headerHeight)
        canvas.drawRect(tempRect, rulerBgPaint)

        // Draw ruler tick marks
        val startSec = (viewport.scrollX / pps).toInt()
        val endSec = ((viewport.scrollX + viewport.width) / pps).toInt() + 1
        for (sec in startSec..endSec) {
            val tickX = (sec * pps - viewport.scrollX).toFloat()
            if (tickX in 0f..viewport.width.toFloat()) {
                canvas.drawLine(tickX, headerHeight - 15f, tickX, headerHeight, rulerTickPaint)
                val label = String.format("%d:%02d", sec / 60, sec % 60)
                rulerTextPaint.getTextBounds(label, 0, label.length, tempTextBounds)
                canvas.drawText(label, tickX - tempTextBounds.width() / 2f, headerHeight - 25f, rulerTextPaint)
            }
        }

        // 5. Draw Playhead
        val playheadX = (engine.currentTime * pps - viewport.scrollX).toFloat()
        if (playheadX in 0f..viewport.width.toFloat()) {
            // Draw Playhead vertical line
            canvas.drawLine(playheadX, headerHeight, playheadX, viewport.height.toFloat(), playheadLinePaint)
            
            // Draw Playhead header polygon (triangle pointing down)
            playheadPath.reset()
            playheadPath.moveTo(playheadX - 15f, 0f)
            playheadPath.lineTo(playheadX + 15f, 0f)
            playheadPath.lineTo(playheadX + 15f, headerHeight * 0.6f)
            playheadPath.lineTo(playheadX, headerHeight)
            playheadPath.lineTo(playheadX - 15f, headerHeight * 0.6f)
            playheadPath.close()
            canvas.drawPath(playheadPath, playheadHeadPaint)
        }

        // 6. Draw Marquee Selection Box Overlay
        if (view.isMarqueeActive) {
            val startX = view.marqueeStartX
            val startY = view.marqueeStartY
            val currentX = view.marqueeCurrentX
            val currentY = view.marqueeCurrentY
            
            val marqueeRect = RectF(
                kotlin.math.min(startX, currentX),
                kotlin.math.min(startY, currentY),
                kotlin.math.max(startX, currentX),
                kotlin.math.max(startY, currentY)
            )
            
            val fillPaint = Paint().apply {
                style = Paint.Style.FILL
                color = 0x336366F1.toInt() // 20% Indigo opacity fill
            }
            val borderPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = 0xFF818CF8.toInt() // Vibrant Indigo
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            canvas.drawRoundRect(marqueeRect, 8f, 8f, fillPaint)
            canvas.drawRoundRect(marqueeRect, 8f, 8f, borderPaint)
        }
    }
}
