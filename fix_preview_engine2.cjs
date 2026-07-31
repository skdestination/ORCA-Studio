const fs = require('fs');
let code = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/PreviewEngine.kt', 'utf8');

// Replace renderSingleFrameImmediate logic
const r1 = `        // Force full resolution quality during scrubbing/seeks to guarantee visual crispness
        val frame = session.composeFrame(seconds, 1.0f)
        renderPipeline.renderFrame(frame.compositionOutput)
        PreviewFrame.release(frame)`;

const r1_replacement = `        // Render occurs inside GLSurfaceView's onDrawFrame via PreviewSurfaceView
        // We just notify listeners that time updated, triggering a requestRender().`;

code = code.replace(r1, r1_replacement);

// Replace onFrameTick logic
const r2 = `        // 1. Compile active frame at the target adaptive scale
        val qualityScale = schedulerInstance.adaptiveQualityScale
        val frame = session.composeFrame(currentSeconds, qualityScale)
        
        // 2. Submit composed frame to the OpenGL Render Pipeline
        val stats = renderPipeline.renderFrame(frame.compositionOutput)
        
        // 3. Log render execution metrics
        metrics.recordFrameRendered(stats.lastFrameRenderTimeNs)
        
        // 4. Dispatch position change updates to listeners
        notifyTimeUpdated(currentSeconds, isScrubbing = false)
        
        // 5. Recycle frame descriptor instantly
        PreviewFrame.release(frame)`;

const r2_replacement = `        // Rendering is handled by PreviewSurfaceView continuously while playing.
        // We just dispatch time updates so the UI can stay in sync.
        notifyTimeUpdated(currentSeconds, isScrubbing = false)`;

code = code.replace(r2, r2_replacement);

fs.writeFileSync('android/app/src/main/java/com/litecut/app/timeline/PreviewEngine.kt', code);
