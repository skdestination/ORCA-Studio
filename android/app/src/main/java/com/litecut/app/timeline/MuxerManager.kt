package com.litecut.app.timeline

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

class MuxerManager(private val outputPath: String) {
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    
    @Volatile
    private var isStarted = false
    
    private val lock = Any()

    init {
        try {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            Log.i("MuxerManager", "Created MediaMuxer for output: $outputPath")
        } catch (e: Exception) {
            Log.e("MuxerManager", "Failed to initialize MediaMuxer", e)
            throw e
        }
    }

    @Synchronized
    fun addVideoTrack(format: MediaFormat) {
        synchronized(lock) {
            val m = muxer ?: return
            if (isStarted) {
                Log.w("MuxerManager", "Attempted to add video track after muxer started.")
                return
            }
            videoTrackIndex = m.addTrack(format)
            Log.d("MuxerManager", "Added Video Track: index = $videoTrackIndex")
            checkStartCondition()
        }
    }

    @Synchronized
    fun addAudioTrack(format: MediaFormat) {
        synchronized(lock) {
            val m = muxer ?: return
            if (isStarted) {
                Log.w("MuxerManager", "Attempted to add audio track after muxer started.")
                return
            }
            audioTrackIndex = m.addTrack(format)
            Log.d("MuxerManager", "Added Audio Track: index = $audioTrackIndex")
            checkStartCondition()
        }
    }

    private fun checkStartCondition() {
        val m = muxer ?: return
        if (isStarted) return

        // We can start if we have added the tracks we intend to mux.
        // For video-only, we can start if videoTrackIndex is set.
        // For full AV export, both must be set.
        if (videoTrackIndex >= 0) {
            m.start()
            isStarted = true
            Log.i("MuxerManager", "MediaMuxer started successfully.")
        }
    }

    fun writeVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            val m = muxer ?: return
            if (!isStarted || videoTrackIndex < 0) {
                Log.w("MuxerManager", "Muxer not ready to write video sample. Started: $isStarted, Track: $videoTrackIndex")
                return
            }
            try {
                m.writeSampleData(videoTrackIndex, buffer, bufferInfo)
            } catch (e: Exception) {
                Log.e("MuxerManager", "Error writing video sample data", e)
            }
        }
    }

    fun writeAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            val m = muxer ?: return
            if (!isStarted || audioTrackIndex < 0) {
                // If there's no audio track, we ignore it
                return
            }
            try {
                m.writeSampleData(audioTrackIndex, buffer, bufferInfo)
            } catch (e: Exception) {
                Log.e("MuxerManager", "Error writing audio sample data", e)
            }
        }
    }

    fun stopAndRelease() {
        synchronized(lock) {
            try {
                if (isStarted) {
                    muxer?.stop()
                    isStarted = false
                    Log.i("MuxerManager", "MediaMuxer stopped.")
                }
            } catch (e: Exception) {
                Log.e("MuxerManager", "Error stopping MediaMuxer", e)
            } finally {
                try {
                    muxer?.release()
                } catch (e: Exception) {
                    Log.e("MuxerManager", "Error releasing MediaMuxer", e)
                }
                muxer = null
                videoTrackIndex = -1
                audioTrackIndex = -1
                Log.i("MuxerManager", "MediaMuxer resources fully released.")
            }
            Unit
        }
    }
}
