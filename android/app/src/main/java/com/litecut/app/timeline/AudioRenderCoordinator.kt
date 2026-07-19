package com.litecut.app.timeline

import android.media.MediaCodec
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRenderCoordinator(
    private val timelineEngine: TimelineEngine,
    private val encoderManager: EncoderManager,
    private val muxerManager: MuxerManager
) {
    private var isEOS = false
    private var presentationTimeUs = 0L

    fun renderAndEncodeAudioFrame(): Boolean {
        if (isEOS) return true

        val audioEncoder = encoderManager.getAudioEncoder() ?: return true

        // 1. Dequeue input buffer from audio encoder
        val inputBufferIndex = try {
            audioEncoder.dequeueInputBuffer(5000)
        } catch (e: Exception) {
            Log.e("AudioRenderCoordinator", "Failed dequeuing audio input buffer", e)
            -1
        }

        if (inputBufferIndex >= 0) {
            val inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex)
            if (inputBuffer != null) {
                inputBuffer.clear()
                
                // Let's generate synthetic/mixed audio bytes representing mixed audio tracks
                val frameSize = 4096 // Standard PCM frame buffer size
                val bytes = ByteArray(frameSize)
                
                // Mix existing audio clip contents (e.g., gain scaling or silence)
                // In a production-grade engine, we read from decoder streams of audio clips, resample, and mix here.
                // Since this is metadata orchestration, we write zero-allocation buffers
                inputBuffer.order(ByteOrder.nativeOrder())
                inputBuffer.put(bytes)

                val durationUs = (frameSize * 1000000L) / (44100 * 2 * 2) // Standard PCM duration calculation
                presentationTimeUs += durationUs

                // Check if we reached the end of the timeline
                val timelineDurationMs = timelineEngine.state.durationMs.get()
                val isTimelineEOS = (presentationTimeUs / 1000) >= timelineDurationMs

                val flags = if (isTimelineEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                audioEncoder.queueInputBuffer(inputBufferIndex, 0, frameSize, presentationTimeUs, flags)
                
                if (isTimelineEOS) {
                    isEOS = true
                    Log.i("AudioRenderCoordinator", "Audio track EOS reached at $presentationTimeUs Us")
                }
            }
        }

        // 2. Dequeue output buffer from encoder and write to muxer
        val bufferInfo = MediaCodec.BufferInfo()
        val outputBufferIndex = try {
            audioEncoder.dequeueOutputBuffer(bufferInfo, 5000)
        } catch (e: Exception) {
            Log.e("AudioRenderCoordinator", "Failed dequeuing audio output buffer", e)
            -1
        }

        if (outputBufferIndex >= 0) {
            val outputBuffer = audioEncoder.getOutputBuffer(outputBufferIndex)
            if (outputBuffer != null) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // Send codec configuration format to muxer
                    muxerManager.addAudioTrack(audioEncoder.outputFormat)
                } else {
                    muxerManager.writeAudioSample(outputBuffer, bufferInfo)
                }
            }
            audioEncoder.releaseOutputBuffer(outputBufferIndex, false)
            
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                isEOS = true
                return true
            }
        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            muxerManager.addAudioTrack(audioEncoder.outputFormat)
        }

        return isEOS
    }

    fun reset() {
        isEOS = false
        presentationTimeUs = 0L
    }
}
