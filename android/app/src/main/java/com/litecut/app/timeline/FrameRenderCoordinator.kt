package com.litecut.app.timeline

import android.media.MediaCodec
import android.util.Log
import java.nio.ByteBuffer

class FrameRenderCoordinator(
    private val timelineEngine: TimelineEngine,
    private val encoderManager: EncoderManager,
    private val frameRenderer: FrameRenderer,
    private val muxerManager: MuxerManager
) {
    private var isEOS = false
    private var currentFrameIndex = 0
    private var totalFrames = 0
    private var frameDurationUs = 33333L // Default for 30 FPS: ~33.3ms

    fun initPipeline(settings: ExportSettings, durationMs: Long) {
        currentFrameIndex = 0
        frameDurationUs = 1_000_000L / settings.frameRate
        totalFrames = ((durationMs / 1000.0) * settings.frameRate).toInt()
        isEOS = false
        Log.i("FrameRenderCoordinator", "Pipeline initialized. Total frames to export: $totalFrames ($settings.frameRate FPS)")
    }

    /**
     * Renders a single frame through the entire stack and drains the video encoder output.
     * Returns true if the export of all video frames has finished (EOS).
     */
    fun renderNextFrame(settings: ExportSettings): Boolean {
        if (isEOS) return true

        val videoEncoder = encoderManager.getVideoEncoder() ?: return true

        if (currentFrameIndex < totalFrames) {
            val presentationTimeUs = currentFrameIndex * frameDurationUs
            val presentationTimeNs = presentationTimeUs * 1000
            val relativeTimeOffset = (presentationTimeUs / 1000.0)

            // 1. Render frame through unified deterministic E2E composition pipeline
            frameRenderer.makeCurrent()
            frameRenderer.renderViewport(settings.resolution.width, settings.resolution.height)
            frameRenderer.clearScreen(0.0f, 0.0f, 0.0f, 1.0f)

            // Execute the deterministic pipeline: Timeline -> Composition -> Keyframes -> Transitions -> Masks -> Text -> Color -> Effects
            val presentationTimeSeconds = presentationTimeUs / 1_000_000.0
            val orcaEngine = OrcaEngine.getInstance()
            val compOutput = orcaEngine.executeFrameDeterministic(presentationTimeSeconds, isExport = true)

            // Draw the compiled, fully resolved frame into the encoder's input surface using our RenderPipeline
            orcaEngine.renderPipeline.renderFrame(compOutput)

            // Swap buffers to send frame to encoder input surface
            frameRenderer.swapBuffers(presentationTimeNs)
            currentFrameIndex++
        } else {
            // Signal End of Stream
            videoEncoder.signalEndOfInputStream()
            isEOS = true
            Log.i("FrameRenderCoordinator", "All frames dispatched. Sent signalEndOfInputStream to video encoder.")
        }

        // 2. Dequeue encoded output buffer from video encoder and write to muxer
        drainEncoder()

        return isEOS && currentFrameIndex >= totalFrames
    }

    private fun drainEncoder() {
        val videoEncoder = encoderManager.getVideoEncoder() ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        // Loop to drain any pending encoded packets
        while (true) {
            val outputBufferIndex = try {
                videoEncoder.dequeueOutputBuffer(bufferInfo, 2000)
            } catch (e: Exception) {
                Log.e("FrameRenderCoordinator", "Failed dequeuing video output buffer", e)
                -1
            }

            if (outputBufferIndex >= 0) {
                val outputBuffer = videoEncoder.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // Send codec configuration format to muxer
                        muxerManager.addVideoTrack(videoEncoder.outputFormat)
                    } else {
                        muxerManager.writeVideoSample(outputBuffer, bufferInfo)
                    }
                }
                videoEncoder.releaseOutputBuffer(outputBufferIndex, false)
                
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isEOS = true
                    break
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                muxerManager.addVideoTrack(videoEncoder.outputFormat)
            } else {
                break // No more buffers available currently
            }
        }
    }

    fun getCurrentFrame(): Int = currentFrameIndex
    fun getTotalFrames(): Int = totalFrames

    fun reset() {
        currentFrameIndex = 0
        totalFrames = 0
        isEOS = false
    }
}
