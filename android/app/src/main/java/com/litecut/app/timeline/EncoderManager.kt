package com.litecut.app.timeline

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

class EncoderManager {
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var videoInputSurface: Surface? = null

    fun initVideoEncoder(settings: ExportSettings): Surface {
        val format = MediaFormat.createVideoFormat(
            settings.videoCodec,
            settings.resolution.width,
            settings.resolution.height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, settings.bitrateBps.toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, settings.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval for precision seekability
            
            // Apply Bitrate Mode
            if (settings.bitrateMode == BitrateMode.CBR) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            } else {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
        }

        try {
            videoEncoder = MediaCodec.createEncoderByType(settings.videoCodec).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                videoInputSurface = createInputSurface()
                start()
            }
            Log.i("EncoderManager", "Initialized video encoder of type: ${settings.videoCodec}")
        } catch (e: Exception) {
            Log.e("EncoderManager", "Failed to start video encoder", e)
            throw e
        }

        return videoInputSurface ?: throw IllegalStateException("Failed to retrieve Input Surface from Video Encoder")
    }

    fun initAudioEncoder(settings: ExportSettings) {
        val format = MediaFormat.createAudioFormat(
            settings.audioCodec,
            settings.audioSampleRate,
            2 // Stereo audio
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, settings.audioBitrateBps)
        }

        try {
            audioEncoder = MediaCodec.createEncoderByType(settings.audioCodec).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            Log.i("EncoderManager", "Initialized audio encoder of type: ${settings.audioCodec}")
        } catch (e: Exception) {
            Log.e("EncoderManager", "Failed to start audio encoder", e)
            throw e
        }
    }

    fun getVideoEncoder(): MediaCodec? = videoEncoder
    fun getAudioEncoder(): MediaCodec? = audioEncoder
    fun getVideoInputSurface(): Surface? = videoInputSurface

    fun releaseVideoEncoder() {
        try {
            videoEncoder?.stop()
        } catch (e: Exception) {
            Log.e("EncoderManager", "Error stopping video encoder", e)
        } finally {
            videoEncoder?.release()
            videoEncoder = null
        }
        videoInputSurface?.release()
        videoInputSurface = null
        Log.d("EncoderManager", "Video encoder released.")
    }

    fun releaseAudioEncoder() {
        try {
            audioEncoder?.stop()
        } catch (e: Exception) {
            Log.e("EncoderManager", "Error stopping audio encoder", e)
        } finally {
            audioEncoder?.release()
            audioEncoder = null
        }
        Log.d("EncoderManager", "Audio encoder released.")
    }

    fun releaseAll() {
        releaseVideoEncoder()
        releaseAudioEncoder()
    }
}
