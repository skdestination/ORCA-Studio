package com.litecut.app.timeline

import android.content.Context
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PreviewRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private val renderPipeline = RenderPipeline.getInstance()
    private val previewEngine = PreviewEngine.getInstance(context)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        renderPipeline.onSurfaceCreated(context)
        previewEngine.invalidate()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        renderPipeline.onSurfaceChanged(width, height)
        previewEngine.invalidate()
    }

    override fun onDrawFrame(gl: GL10?) {
        // The render pipeline actually renders the frame when previewEngine calls it.
        // Wait, PreviewEngine uses SeekController and PreviewScheduler to trigger rendering.
        // So does PreviewEngine call renderPipeline.renderFrame() directly? Yes.
        // That means PreviewEngine might be rendering from a different thread than the GL thread!
        // GLSurfaceView creates its own GL thread. 
        // If PreviewEngine tries to call renderSingleFrameImmediate from the main thread, it won't have a GL context.
    }
}
