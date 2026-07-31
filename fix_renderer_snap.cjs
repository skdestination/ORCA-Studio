const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineRenderer.kt', 'utf8');

const drawSnapGuideInsert = `
    private val snapLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f // will scale with density
    }

    private fun drawSnapGuides(canvas: Canvas, viewport: Viewport, gestureHandler: TimelineGestureHandler, density: Float) {
        val snapResult = gestureHandler.activeSnapResult ?: return
        if (!snapResult.isSnapped) return

        val guides = SnapEngine.getInstance().generateGuidesFromSnapResult(snapResult)
        
        snapLinePaint.strokeWidth = 1.5f * density
        for (guide in guides) {
            snapLinePaint.color = guide.snapLineColor
            val x = (guide.timeSeconds * engine.pixelsPerSecond - viewport.scrollX).toFloat()
            canvas.drawLine(x, headerHeight, x, canvas.height.toFloat(), snapLinePaint)
        }
    }
`;

code = code.replace('    private val playheadPath = Path()', drawSnapGuideInsert + '\n    private val playheadPath = Path()');

const onDrawInsert = `
        // Draw drag previews
        drawDragPreview(canvas, viewport, gestureHandler, density)
        
        // Draw snap guides
        drawSnapGuides(canvas, viewport, gestureHandler, density)
`;

code = code.replace(
    '        // Draw drag previews\n        drawDragPreview(canvas, viewport, gestureHandler, density)',
    onDrawInsert
);

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineRenderer.kt', code);
