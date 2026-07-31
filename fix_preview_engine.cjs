const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/PreviewEngine.kt', 'utf8');

// We can modify renderSingleFrameImmediate to not actually call RenderPipeline.renderFrame if we want the GLSurfaceView to do it.
// Or we can just let GLSurfaceView handle the render Frame.

// But wait, actually, if we modify PreviewEngine to just request the view to render...
