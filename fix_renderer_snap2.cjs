const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineRenderer.kt', 'utf8');

code = code.replace(
    'private fun drawSnapGuides(canvas: Canvas, viewport: Viewport, gestureHandler: TimelineGestureHandler, density: Float) {',
    'private fun drawSnapGuides(canvas: Canvas, viewport: Viewport, gestureHandler: TimelineGestureHandler, density: Float, engine: TimelineEngine) {'
);

code = code.replace(
    'drawSnapGuides(canvas, viewport, gestureHandler, density)',
    'drawSnapGuides(canvas, viewport, gestureHandler, density, engine)'
);

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineRenderer.kt', code);
