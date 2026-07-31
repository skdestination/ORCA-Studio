package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

class PreviewSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var renderThread: RenderThread? = null

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("PreviewSurfaceView", "surfaceCreated")
        renderThread = RenderThread(holder, context)
        renderThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("PreviewSurfaceView", "surfaceChanged: ${width}x${height}")
        renderThread?.onSurfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("PreviewSurfaceView", "surfaceDestroyed")
        renderThread?.shutdown()
        renderThread?.join()
        renderThread = null
    }

    private class RenderThread(
        private val surfaceHolder: SurfaceHolder,
        private val context: Context
    ) : Thread("PreviewRenderThread") {
        
        @Volatile private var isRunning = true
        @Volatile private var isDirty = true
        private var viewportWidth = 0
        private var viewportHeight = 0
        private var widthChanged = false

        private val frameRenderer = FrameRenderer()
        private val renderPipeline = RenderPipeline.getInstance()
        private val previewEngine = PreviewEngine.getInstance(context)

        private val syncListener = object : PlaybackSyncListener {
            override fun onStateChanged(newState: PlaybackState) {
                if (newState != PlaybackState.PLAYING) {
                    requestRender()
                }
            }
            override fun onTimeUpdated(seconds: Double, isScrubbing: Boolean) {
                requestRender()
            }
            override fun onSeekStarted(targetSeconds: Double) {}
            override fun onSeekCompleted(actualSeconds: Double) { requestRender() }
            override fun onFrameDropped(targetTime: Double, actualTime: Double) {}
            override fun onBufferingChanged(isBuffering: Boolean) {}
        }

        fun onSurfaceChanged(width: Int, height: Int) {
            synchronized(this) {
                viewportWidth = width
                viewportHeight = height
                widthChanged = true
                isDirty = true
                (this as java.lang.Object).notifyAll()
            }
        }

        fun requestRender() {
            synchronized(this) {
                isDirty = true
                (this as java.lang.Object).notifyAll()
            }
        }

        fun shutdown() {
            synchronized(this) {
                isRunning = false
                (this as java.lang.Object).notifyAll()
            }
        }

        override fun run() {
            frameRenderer.initGL(surfaceHolder.surface)
            renderPipeline.onSurfaceCreated(context)
            previewEngine.registerListener(syncListener)

            while (isRunning) {
                val playing = previewEngine.isPlaying()
                
                synchronized(this) {
                    if (!playing && !isDirty && isRunning) {
                        try {
                            (this as java.lang.Object).wait()
                        } catch (e: InterruptedException) {}
                    }
                    if (!isRunning) return@synchronized
                    isDirty = false
                    
                    if (widthChanged) {
                        renderPipeline.onSurfaceChanged(viewportWidth, viewportHeight)
                        widthChanged = false
                    }
                }

                if (!isRunning) break

                val session = previewEngine.getActiveSession()
                if (session != null) {
                    val currentSeconds = previewEngine.getCurrentTimeSeconds()
                    val frame = session.composeFrame(currentSeconds, 1.0f)
                    
                    frameRenderer.makeCurrent()
                    renderPipeline.renderFrame(frame.compositionOutput)
                    frameRenderer.swapBuffers(System.nanoTime())
                    
                    PreviewFrame.release(frame)
                } else {
                    frameRenderer.makeCurrent()
                    android.opengl.GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                    android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT)
                    frameRenderer.swapBuffers(System.nanoTime())
                }
            }

            previewEngine.unregisterListener(syncListener)
            renderPipeline.onSurfaceDestroyed()
            frameRenderer.release()
        }
    }
}
