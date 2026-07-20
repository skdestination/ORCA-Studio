package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.tasks.CancellationToken
import java.io.File

class ExportPipeline(
    private val context: Context,
    private val timelineEngine: TimelineEngine,
    private val settings: ExportSettings,
    private val outputPath: String
) {
    private val stats = ExportStatistics()
    val progressTracker = ProgressTracker(stats)

    @Volatile
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
        Log.w("ExportPipeline", "Export cancellation requested.")
    }

    /**
     * Executes the main export render loop on the calling background thread.
     */
    fun execute(cancellationToken: CancellationToken? = null): Boolean {
        stats.reset()
        isCancelled = false

        val encoderManager = EncoderManager()
        val muxerManager = MuxerManager(outputPath)
        val frameRenderer = FrameRenderer()
        val frameRenderCoordinator = FrameRenderCoordinator(timelineEngine, encoderManager, frameRenderer, muxerManager)
        val audioRenderCoordinator = AudioRenderCoordinator(timelineEngine, encoderManager, muxerManager)

        try {
            val durationMs = (timelineEngine.getTotalDurationSeconds() * 1000).toLong()
            if (durationMs <= 0) {
                progressTracker.notifyError("Timeline duration is 0. Cannot export an empty timeline.")
                return false
            }

            // 1. Initialize Encoders & GL Surface
            val inputSurface = encoderManager.initVideoEncoder(settings)
            frameRenderer.initGL(inputSurface)
            encoderManager.initAudioEncoder(settings)

            // 2. Initialize Coordinators
            frameRenderCoordinator.initPipeline(settings, durationMs)
            audioRenderCoordinator.reset()

            Log.i("ExportPipeline", "Export render loop started for output: $outputPath")
            
            var videoFinished = false
            var audioFinished = false

            // Main rendering loop
            while (!videoFinished || !audioFinished) {
                // Respect cancel flags and cooperative token cancellations
                if (isCancelled || (cancellationToken != null && cancellationToken.isCancelled())) {
                    Log.w("ExportPipeline", "Export render loop cancelled by user.")
                    progressTracker.notifyError("Export Cancelled")
                    break
                }

                val renderStart = System.currentTimeMillis()

                // Render video frame
                if (!videoFinished) {
                    videoFinished = frameRenderCoordinator.renderNextFrame(settings)
                }

                // Render audio frame
                if (!audioFinished) {
                    audioFinished = audioRenderCoordinator.renderAndEncodeAudioFrame()
                }

                val renderDuration = System.currentTimeMillis() - renderStart

                // Update progress of the active video frames
                progressTracker.notifyProgress(
                    frameRenderCoordinator.getCurrentFrame(),
                    frameRenderCoordinator.getTotalFrames(),
                    renderDuration
                )
            }

            if (isCancelled || (cancellationToken != null && cancellationToken.isCancelled())) {
                // Delete incomplete files
                File(outputPath).delete()
                return false
            }

            Log.i("ExportPipeline", "Export render loop finished successfully.")
            progressTracker.notifyComplete(outputPath)
            return true

        } catch (e: Exception) {
            Log.e("ExportPipeline", "Critical error in export pipeline render loop", e)
            progressTracker.notifyError(e.message ?: "Unknown pipeline error")
            
            // Cleanup incomplete files
            try {
                File(outputPath).delete()
            } catch (ignored: Exception) {}
            return false
        } finally {
            // Safe teardown of all native hardware codecs and GLES contexts
            encoderManager.releaseAll()
            frameRenderer.release()
            muxerManager.stopAndRelease()
            Log.i("ExportPipeline", "All pipeline hardware assets fully released.")
        }
    }
}
