const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineGestureHandler.kt', 'utf8');

// 1. Enum
code = code.replace(
    'NONE, SCRUBBING, DRAGGING, SCROLLING, MARQUEE_SELECT',
    'NONE, SCRUBBING, DRAGGING, SCROLLING, MARQUEE_SELECT, TRIMMING'
);

// 2. Variables
const varsInsert = `    var marqueeCurrentY: Float = 0f

    // Trimming
    var trimClipId: String? = null
    var trimSide: String? = null
    var trimStartDuration = 0.0
    var trimStartLeft = 0.0
`;
code = code.replace('    var marqueeCurrentY: Float = 0f\n', varsInsert);

// 3. hitTestTrimHandle
const hitTestInsert = `
    private fun hitTestTrimHandle(x: Float, y: Float): Pair<Clip, String>? {
        val pps = engine.pixelsPerSecond
        val sortedLayers = engine.getAllLayers().sortedBy { it.order }
        val density = context.resources.displayMetrics.density
        val handlePadding = 2f * density
        val handleWidth = 12f * density
        val hitPadding = 20f * density // extra padding for fat fingers

        for (clipId in engine.selectedClipIds) {
            val clip = engine.getClip(clipId) ?: continue
            val layerIndex = sortedLayers.indexOfFirst { it.id == clip.layerId }
            if (layerIndex == -1) continue

            val clipY = renderer.headerHeight + layerIndex * (renderer.trackHeight + renderer.trackSpacing) - viewport.scrollY.toFloat()
            val clipBottom = clipY + renderer.trackHeight

            if (y >= clipY - hitPadding && y <= clipBottom + hitPadding) {
                val clipLeft = (clip.leftSeconds * pps - viewport.scrollX).toFloat()
                val clipRight = (clipLeft + clip.durationSeconds * pps).toFloat()

                // Left handle
                val leftHandleLeft = clipLeft + handlePadding - hitPadding
                val leftHandleRight = clipLeft + handlePadding + handleWidth + hitPadding
                if (x in leftHandleLeft..leftHandleRight) {
                    return Pair(clip, "left")
                }

                // Right handle
                val rightHandleLeft = clipRight - handlePadding - handleWidth - hitPadding
                val rightHandleRight = clipRight - handlePadding + hitPadding
                if (x in rightHandleLeft..rightHandleRight) {
                    return Pair(clip, "right")
                }
            }
        }
        return null
    }

    private fun hitTestClip(x: Float, y: Float): Clip? {
`;
code = code.replace('    private fun hitTestClip(x: Float, y: Float): Clip? {', hitTestInsert);

// 4. ACTION_DOWN
const actionDownReplace = `                if (y < renderer.headerHeight) {
                    touchMode = TouchMode.SCRUBBING
                    scrubPlayhead(x)
                } else {
                    val trimHit = hitTestTrimHandle(x, y)
                    if (trimHit != null) {
                        touchMode = TouchMode.TRIMMING
                        trimClipId = trimHit.first.id
                        trimSide = trimHit.second
                        trimStartDuration = trimHit.first.durationSeconds
                        trimStartLeft = trimHit.first.leftSeconds
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
                }`;
const actionDownRegex = /                if \(y < renderer\.headerHeight\) \{[\s\S]*?touchMode = TouchMode\.SCROLLING\n                    \}\n                \}/;
code = code.replace(actionDownRegex, actionDownReplace);

// 5. ACTION_MOVE
const actionMoveReplace = `                    TouchMode.DRAGGING -> {
                        view.touchX = x
                        view.touchY = y
                        updateDragPreview(x, y)
                        view.invalidate()
                    }
                    TouchMode.TRIMMING -> {
                        val dx = x - downX
                        val deltaSeconds = dx / engine.pixelsPerSecond
                        
                        val clipId = trimClipId ?: return true
                        val clip = engine.getClip(clipId) ?: return true
                        val originalDuration = trimStartDuration
                        val originalLeft = trimStartLeft
                        
                        if (trimSide == "right") {
                            var proposedDuration = originalDuration + deltaSeconds
                            if (proposedDuration < 0.5) proposedDuration = 0.5
                            clip.durationSeconds = proposedDuration
                        } else {
                            var proposedLeft = originalLeft + deltaSeconds
                            var proposedDuration = originalDuration - deltaSeconds
                            if (proposedDuration < 0.5) {
                                proposedLeft = originalLeft + originalDuration - 0.5
                                proposedDuration = 0.5
                            }
                            val deltaTrimStart = proposedLeft - originalLeft
                            val originalTrimStart = clip.additionalProperties["_originalTrimStart"] as? Double ?: clip.trimStartSeconds
                            clip.additionalProperties["_originalTrimStart"] = originalTrimStart
                            clip.trimStartSeconds = kotlin.math.max(0.0, originalTrimStart + deltaTrimStart)
                            clip.leftSeconds = proposedLeft
                            clip.durationSeconds = proposedDuration
                        }
                        view.invalidate()
                    }`;
const actionMoveRegex = /                    TouchMode\.DRAGGING -> \{[\s\S]*?view\.invalidate\(\)\n                    \}/;
code = code.replace(actionMoveRegex, actionMoveReplace);

// 6. ACTION_UP
const actionUpReplace = `                } else if (touchMode == TouchMode.TRIMMING && trimClipId != null) {
                    val dx = x - downX
                    val deltaSeconds = dx / engine.pixelsPerSecond
                    val clip = engine.getClip(trimClipId!!)
                    if (clip != null) {
                        // revert preview
                        clip.leftSeconds = trimStartLeft
                        clip.durationSeconds = trimStartDuration
                        if (clip.additionalProperties.has("_originalTrimStart")) {
                            clip.trimStartSeconds = clip.additionalProperties["_originalTrimStart"] as Double
                            clip.additionalProperties.remove("_originalTrimStart")
                        }
                        
                        // Execute command
                        val cmd = TrimCommand(clip.id, trimSide!!, deltaSeconds.toDouble(), true, engine.currentTime)
                        engine.executeCommand(cmd)
                    }
                    touchMode = TouchMode.NONE
                    trimClipId = null
                    trimSide = null
                    view.invalidate()
                } else if (touchMode == TouchMode.DRAGGING && draggedClipId != null) {`;
const actionUpRegex = /                \} else if \(touchMode == TouchMode\.DRAGGING && draggedClipId != null\) \{/;
code = code.replace(actionUpRegex, actionUpReplace);

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineGestureHandler.kt', code);
