package com.litecut.app.timeline

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.litecut.app.timeline.resources.ResourceManager

class RenderPipeline private constructor() {
    private val textureManager = TextureManager.getInstance()
    private val frameBufferPool = FrameBufferPool.getInstance()
    private val shaderManager = ShaderManager.getInstance()
    private val stats = RenderStatistics()

    // Active rendering context containing viewport matrices and runtime configs
    private val renderContext = RenderContext()
    
    // Master flag indicating whether the pipeline is initialized with an active GL context
    @Volatile
    private var isGlContextActive = false

    companion object {
        @Volatile
        private var instance: RenderPipeline? = null

        fun getInstance(): RenderPipeline {
            return instance ?: synchronized(this) {
                instance ?: RenderPipeline().also { instance = it }
            }
        }
    }

    /**
     * Initializes the rendering pipeline under an active GL context thread.
     * Hooks up pools and caches with ORCA's ResourceManager.
     */
    fun onSurfaceCreated(context: Context) {
        synchronized(this) {
            isGlContextActive = true
            shaderManager.handleContextLoss() // Safe reset
            
            // Register caches under ResourceManager to automatically free memory under system pressure
            val resourceManager = ResourceManager.getInstance(context)
            resourceManager.registerCache(textureManager.categoryName, textureManager)
            resourceManager.registerCache(frameBufferPool.categoryName, frameBufferPool)
            
            Log.i("RenderPipeline", "Native OpenGL Render Pipeline successfully initialized on GPU thread.")
        }
    }

    /**
     * Updates viewport resolution metrics on size modifications.
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        synchronized(this) {
            renderContext.viewportWidth = width
            renderContext.viewportHeight = height
            renderContext.resetMatrices()
            GLES20.glViewport(0, 0, width, height)
            Log.d("RenderPipeline", "Viewport bounds updated to: ${width}x${height}")
        }
    }

    /**
     * Executes the main GPU frame rendering pass.
     * Fully zero-allocation inside the render loop to secure high 120 FPS performance.
     */
    fun renderFrame(compositionOutput: CompositionOutput): RenderStatistics {
        if (!isGlContextActive) {
            return stats
        }

        val startTime = System.nanoTime()

        synchronized(this) {
            // Update context runtime clock
            renderContext.currentTimeSeconds = compositionOutput.timeSeconds
            renderContext.isProxyMode = compositionOutput.isProxyMode

            val onscreenTarget = OnscreenTarget(renderContext.viewportWidth, renderContext.viewportHeight)

            // 1. Compile Composition Output into an executable GPU Render Graph
            val renderGraph = RenderGraph.compile(
                output = compositionOutput,
                context = renderContext,
                onscreenTarget = onscreenTarget,
                frameBufferPool = frameBufferPool,
                textureManager = textureManager
            )

            // 2. Execute GPU rendering passes
            renderGraph.execute(renderContext, shaderManager, stats)

            // 3. Cleanup graph resources (non-allocating clear)
            renderGraph.clear()
        }

        val endTime = System.nanoTime()
        stats.recordFrame(endTime - startTime)

        // Record resource statistics
        stats.setFboCount(frameBufferPool.getCurrentSizeBytes().toInt() / (renderContext.viewportWidth * renderContext.viewportHeight * 4))
        stats.setTextureCount(textureManager.getCurrentSizeBytes().toInt() / (256 * 256 * 4)) // Estimated generic count

        return stats
    }

    /**
     * Resets rendering resources and clears allocations on playback stop.
     */
    fun onSurfaceDestroyed() {
        synchronized(this) {
            isGlContextActive = false
            shaderManager.release()
            textureManager.clear()
            frameBufferPool.clear()
            stats.reset()
            Log.w("RenderPipeline", "Render pipeline surface destroyed. GPU resource pools cleared.")
        }
    }

    fun getStats(): RenderStatistics = stats
}
