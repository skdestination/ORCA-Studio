const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineGestureHandler.kt', 'utf8');

const varsInsert = `    var trimStartLeft = 0.0

    // Snapping
    var activeSnapResult: SnapResult? = null
`;
code = code.replace('    var trimStartLeft = 0.0\n', varsInsert);

// In dragHoldRunnable:
code = code.replace(
    'updateDragPreview(downX, downY)',
    `SnapEngine.getInstance().prepareDragSession(pendingClip!!.id, engine, viewport, engine.pixelsPerSecond)
            updateDragPreview(downX, downY)`
);

// In updateDragPreview (for DRAGGING):
code = code.replace(
    'proposedLeftSeconds = max(0.0, timelineLeftPixel / pps)',
    `val rawLeft = max(0.0, timelineLeftPixel / pps)
        activeSnapResult = SnapEngine.getInstance().snapClip(rawLeft, clip.durationSeconds, pps)
        proposedLeftSeconds = if (activeSnapResult?.isSnapped == true) activeSnapResult!!.snappedTimeSeconds else rawLeft`
);

// In TRIMMING ACTION_DOWN:
code = code.replace(
    'trimStartLeft = trimHit.first.leftSeconds',
    `trimStartLeft = trimHit.first.leftSeconds
                        SnapEngine.getInstance().prepareDragSession(trimHit.first.id, engine, viewport, engine.pixelsPerSecond)`
);

// In TRIMMING ACTION_MOVE:
code = code.replace(
    'var proposedLeft = originalLeft + deltaSeconds',
    `var proposedLeft = originalLeft + deltaSeconds
                            activeSnapResult = SnapEngine.getInstance().snapClip(proposedLeft, originalDuration - deltaSeconds, engine.pixelsPerSecond)
                            if (activeSnapResult?.isSnapped == true) {
                                proposedLeft = activeSnapResult!!.snappedTimeSeconds
                                deltaSeconds = proposedLeft - originalLeft
                            }`
);

// For Right Trim, snap the RIGHT edge. Wait, SnapEngine expects the LEFT edge of the clip to be passed in!
// Let's look at TRIMMING ACTION_MOVE again. 
// If trimSide == "right", the left edge is constant. We want to snap the right edge.
// SnapEngine.snapClip takes (proposedLeftSeconds, durationSeconds). 
// Let's modify the right trim logic:
const rightTrimReplace = `                        if (trimSide == "right") {
                            var proposedDuration = originalDuration + deltaSeconds
                            activeSnapResult = SnapEngine.getInstance().snapClip(originalLeft, proposedDuration, engine.pixelsPerSecond)
                            if (activeSnapResult?.isSnapped == true && activeSnapResult!!.edgeSnapped == "end") {
                                // wait, snapClip snaps EITHER start or end. If it snaps the end, it modifies snappedTimeSeconds? No, snappedTimeSeconds is always the snapped LEFT edge.
                                // Let's just pass originalLeft, and it will snap the end.
                                // We can infer the snapped duration:
                                val shift = activeSnapResult!!.offsetSeconds
                                proposedDuration -= shift
                            }
                            if (proposedDuration < 0.5) proposedDuration = 0.5
                            clip.durationSeconds = proposedDuration
                        } else {`;
const rightTrimRegex = /                        if \(trimSide == "right"\) \{[\s\S]*?clip\.durationSeconds = proposedDuration\n                        \} else \{/;
code = code.replace(rightTrimRegex, rightTrimReplace);

// ACTION_UP Cleanup:
code = code.replace(
    'touchMode = TouchMode.NONE\n                velocityTracker?.recycle()',
    `touchMode = TouchMode.NONE
                SnapEngine.getInstance().endDragSession()
                activeSnapResult = null
                velocityTracker?.recycle()`
);

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineGestureHandler.kt', code);
