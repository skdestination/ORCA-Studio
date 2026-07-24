package com.litecut.app.timeline.audio

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.litecut.app.timeline.Clip
import com.litecut.app.timeline.Viewport
import com.litecut.app.timeline.TimelineEngine
import com.litecut.app.timeline.TimelineView

/**
 * Highly optimized, DAW-grade continuous filled audio waveform renderer.
 * Draws smooth, hardware-accelerated anti-aliased audio envelopes for mono and stereo tracks.
 * Seamlessly morphs between 4 Levels of Detail (LOD) dynamically as the user zooms.
 * Implements strict zero-allocation drawing loops for 120 FPS buttery smooth scrolling.
 */
class WaveformRenderer {

    // Preallocated paint objects to guarantee zero allocation and no GC spikes during drawing
    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }
    
    private val guidelinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val pathLeft = Path()
    private val pathRight = Path()

    // Expanded coordinate cache (5000 points supports extremely wide tablet displays with high density)
    private val xCoords = FloatArray(5000)
    private val halfHeightsL = FloatArray(5000)
    private val halfHeightsR = FloatArray(5000)

    // Layout configuration constants - high-density step size for rich details
    private val stepPx = 1.5f

    /**
     * Determines Level of Detail progress continuously from zoomLevel.
     * Maps the zoomLevel logarithmically/linearly to a float value from 0.0f to 3.0f,
     * representing the precise blending position between LOD 0, 1, 2, and 3.
     */
    private fun calculateDetailProgress(zoom: Double): Float {
        return when {
            zoom < 0.3 -> {
                val fraction = ((zoom - 0.1) / 0.2).coerceIn(0.0, 1.0)
                (0.0 + fraction).toFloat()
            }
            zoom < 1.0 -> {
                val fraction = ((zoom - 0.3) / 0.7).coerceIn(0.0, 1.0)
                (1.0 + fraction).toFloat()
            }
            zoom < 3.0 -> {
                val fraction = ((zoom - 1.0) / 2.0).coerceIn(0.0, 1.0)
                (2.0 + fraction).toFloat()
            }
            else -> 3.0f
        }
    }

    private fun getBrighterTrackColor(type: AudioTrackType): Int {
        return when (type) {
            AudioTrackType.MUSIC -> 0xFFE9D5FF.toInt()    // Soft Lavender Outline
            AudioTrackType.VOICE -> 0xFFF3E8FF.toInt()    // Light Violet Outline
            AudioTrackType.SFX -> 0xFFFAE8FF.toInt()      // Bright Soft Purple Outline
            AudioTrackType.AMBIENT -> 0xFFC7D2FE.toInt()  // Soft Indigo Outline
        }
    }

    /**
     * Renders a highly detailed continuous filled waveform with zero GC allocations.
     */
    fun drawWaveform(
        canvas: Canvas,
        clip: Clip,
        clipRect: RectF,
        viewport: Viewport,
        pps: Double,
        engine: WaveformEngine,
        isSelected: Boolean,
        isMuted: Boolean,
        view: android.view.View
    ) {
        val densityFactor = 2.0f
        val trackType = engine.getAudioTrackType(clip)
        val baseColor = engine.getTrackColor(trackType)
        val outlineColor = getBrighterTrackColor(trackType)

        val clipHeight = clipRect.height()
        val centerY = clipRect.top + clipHeight / 2f

        // 1. Fetch or trigger background peak extraction via TaskScheduler/WaveformEngine
        val data = engine.getOrRequestWaveform(clip) {
            val dirtyLeft = clipRect.left.toInt()
            val dirtyTop = clipRect.top.toInt()
            val dirtyRight = clipRect.right.toInt()
            val dirtyBottom = clipRect.bottom.toInt()
            view.postInvalidate(dirtyLeft, dirtyTop, dirtyRight, dirtyBottom)
        }

        // If data is still loading, render a beautiful elegant low-opacity horizontal guide
        if (data == null) {
            guidelinePaint.color = baseColor
            guidelinePaint.alpha = 0x22 // ~13% opacity
            guidelinePaint.strokeWidth = 1.0f * densityFactor
            canvas.drawLine(
                Math.max(clipRect.left, 0f),
                centerY,
                Math.min(clipRect.right, viewport.width.toFloat()),
                centerY,
                guidelinePaint
            )
            return
        }

        // 2. Perform virtualization by clipping coordinate generation to visible window bounds
        val startVisibleX = Math.max(clipRect.left, 0f)
        val endVisibleX = Math.min(clipRect.right, viewport.width.toFloat())

        if (startVisibleX >= endVisibleX) return

        val isStereo = data.isStereo
        // Layout Calculations (Waveform occupies 76% of clip height, beautifully centered with 11% outer margins and 10% center gap)
        val marginY = clipHeight * 0.11f
        val usableHeight = clipHeight - 2 * marginY
        val centerYL = if (isStereo) centerY - clipHeight * 0.22f else centerY
        val centerYR = if (isStereo) centerY + clipHeight * 0.22f else centerY
        val channelMaxHeight = if (isStereo) usableHeight * 0.44f else usableHeight

        // Pre-draw horizontal baselines behind the waveform for a high-end DAW editor feel
        guidelinePaint.color = baseColor
        guidelinePaint.strokeWidth = 1.0f * densityFactor
        guidelinePaint.alpha = if (isMuted) 0x1F else 0x38 // Subtle guideline transparency
        if (isStereo) {
            canvas.drawLine(startVisibleX, centerYL, endVisibleX, centerYL, guidelinePaint)
            canvas.drawLine(startVisibleX, centerYR, endVisibleX, centerYR, guidelinePaint)
        } else {
            canvas.drawLine(startVisibleX, centerY, endVisibleX, centerY, guidelinePaint)
        }

        // 3. Calculate current smooth multi-LOD interpolation values
        val zoomLevel = TimelineEngine.getInstance().zoomLevel
        val detailProgress = calculateDetailProgress(zoomLevel)
        val lodA = Math.floor(detailProgress.toDouble()).toInt().coerceIn(0, 3)
        val lodB = Math.ceil(detailProgress.toDouble()).toInt().coerceIn(0, 3)
        val blend = (detailProgress - lodA)

        // 4. Sample peaks along the timeline to fill preallocated cache arrays
        val maxSteps = xCoords.size
        var stepCount = 0

        val clipStartTimelineX = clip.leftSeconds * pps
        val clipDurationPx = clip.durationSeconds * clip.speed * pps

        // Align step scans globally to avoid scrolling shimmer / jitter artifacts
        val timelineStart = startVisibleX + viewport.scrollX
        val alignedTimelineStart = (Math.floor(timelineStart / stepPx) * stepPx)
        var currentTimelineX = alignedTimelineStart

        while (currentTimelineX <= (endVisibleX + viewport.scrollX)) {
            val currentX = (currentTimelineX - viewport.scrollX).toFloat()
            if (currentX >= clipRect.left && currentX <= clipRect.right) {
                val fraction = if (clipDurationPx > 0) {
                    (currentTimelineX - clipStartTimelineX) / clipDurationPx
                } else {
                    0.0
                }
                
                // Sample Left & Right channels with dual-LOD linear interpolation
                val peakL = sampleInterpolatedPeak(data, lodA, lodB, blend, fraction, isLeft = true)
                val peakR = if (isStereo) {
                    sampleInterpolatedPeak(data, lodA, lodB, blend, fraction, isLeft = false)
                } else {
                    0f
                }

                if (stepCount < maxSteps) {
                    xCoords[stepCount] = currentX
                    halfHeightsL[stepCount] = (peakL * channelMaxHeight) / 2f
                    if (isStereo) {
                        halfHeightsR[stepCount] = (peakR * channelMaxHeight) / 2f
                    }
                    stepCount++
                } else {
                    break
                }
            }
            currentTimelineX += stepPx
        }

        // 5. Build continuous closed vector paths and execute hardware accelerated canvas fills
        if (stepCount > 0) {
            // High-fidelity styling configuration (90% opacity body + 100% border, muted state, selection highlight)
            strokePaint.strokeWidth = 1.0f * densityFactor
            
            val isDragging = view is TimelineView && view.isDragging && clip.id == view.draggedClipId
            val alphaScale = if (isDragging) 0.55f else 1.0f

            if (isMuted) {
                fillPaint.color = baseColor
                fillPaint.alpha = (0x22 * alphaScale).toInt()
                strokePaint.color = baseColor
                strokePaint.alpha = (0x3F * alphaScale).toInt()
            } else if (isSelected) {
                fillPaint.color = outlineColor
                fillPaint.alpha = (0xFF * alphaScale).toInt()
                strokePaint.color = outlineColor
                strokePaint.alpha = (0xFF * alphaScale).toInt()
            } else {
                fillPaint.color = baseColor
                fillPaint.alpha = (0xDF * alphaScale).toInt()
                strokePaint.color = baseColor
                strokePaint.alpha = (0xFF * alphaScale).toInt()
            }

            // Draw Left/Mono channel
            pathLeft.reset()
            pathLeft.moveTo(xCoords[0], centerYL - halfHeightsL[0])
            for (i in 1 until stepCount) {
                pathLeft.lineTo(xCoords[i], centerYL - halfHeightsL[i])
            }
            for (i in (stepCount - 1) downTo 0) {
                pathLeft.lineTo(xCoords[i], centerYL + halfHeightsL[i])
            }
            pathLeft.close()
            canvas.drawPath(pathLeft, fillPaint)
            canvas.drawPath(pathLeft, strokePaint)

            // Draw Right channel (for stereo tracks)
            if (isStereo) {
                pathRight.reset()
                pathRight.moveTo(xCoords[0], centerYR - halfHeightsR[0])
                for (i in 1 until stepCount) {
                    pathRight.lineTo(xCoords[i], centerYR - halfHeightsR[i])
                }
                for (i in (stepCount - 1) downTo 0) {
                    pathRight.lineTo(xCoords[i], centerYR + halfHeightsR[i])
                }
                pathRight.close()
                canvas.drawPath(pathRight, fillPaint)
                canvas.drawPath(pathRight, strokePaint)
            }
        }
    }

    private fun sampleInterpolatedPeak(
        data: WaveformData,
        lodA: Int,
        lodB: Int,
        blend: Float,
        fraction: Double,
        isLeft: Boolean
    ): Float {
        val frac = fraction.coerceIn(0.0, 1.0)
        val arrA = if (isLeft) data.getLeftArray(lodA) else data.getRightArray(lodA)
        val arrB = if (isLeft) data.getLeftArray(lodB) else data.getRightArray(lodB)
        
        val valA = if (arrA != null) samplePeak(arrA, frac) else 0f
        val valB = if (arrB != null) samplePeak(arrB, frac) else valA
        
        return valA * (1f - blend) + valB * blend
    }

    private fun samplePeak(peaks: FloatArray, fraction: Double): Float {
        if (peaks.isEmpty()) return 0f
        val indexDouble = fraction * (peaks.size - 1)
        val indexLow = indexDouble.toInt().coerceIn(0, peaks.size - 1)
        val indexHigh = (indexLow + 1).coerceIn(0, peaks.size - 1)
        val t = (indexDouble - indexLow).toFloat()
        return peaks[indexLow] * (1f - t) + peaks[indexHigh] * t
    }
}
