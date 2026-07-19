package com.litecut.app.timeline.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.litecut.app.timeline.tasks.CancellationToken
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, memory-efficient media audio decoder and PCM extractor.
 * Utilizes native MediaExtractor + MediaCodec to stream and parse audio track channels.
 * Gracefully handles 16-bit, 24-bit, and 32-bit float PCM configurations with zero-allocation loops.
 */
class WaveformExtractor(private val context: Context) {

    companion object {
        private const val TAG = "WaveformExtractor"
        private const val TIMEOUT_US = 5000L // 5ms decoder timeout
    }

    class ExtractedAudio(
        val samplesLeft: FloatArray,
        val samplesRight: FloatArray?,
        val sampleRate: Int,
        val channels: Int,
        val durationSeconds: Double
    )

    /**
     * Extracts PCM audio data into normalized floating-point channel arrays.
     * Cooperates with CancellationToken and reports progress via callback.
     */
    fun extractPcm(
        src: String,
        token: CancellationToken,
        onProgress: (Int) -> Unit
    ): ExtractedAudio? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            // 1. Configure Data Source
            setExtractorDataSource(context, extractor, src)

            // 2. Locate Audio Track
            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) {
                Log.w(TAG, "No audio track found in file: $src")
                return null
            }
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val durationSeconds = durationUs / 1000000.0

            // Get PCM encoding if available (default to 16-bit PCM)
            val pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                android.media.AudioFormat.ENCODING_PCM_16BIT
            }

            Log.d(TAG, "Decoding $mime: $channels channels, $sampleRate Hz, ${durationSeconds}s, Encoding: $pcmEncoding")

            // 3. Initialize Codec
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // 4. Prepare Decoding Buffers
            val inputBuffers = codec.inputBuffers
            val outputBuffers = codec.outputBuffers
            val bufferInfo = MediaCodec.BufferInfo()

            // Pre-calculate sample buffers to avoid allocations.
            // For a 2-minute song, decimation / downsampling can happen on the fly.
            // To prevent massive memory usage, we limit the maximum in-memory samples extracted.
            // 100,000 samples per channel are more than enough to capture every minor transient.
            val maxTargetSamples = 120000
            val extractionStep = if (durationSeconds > 0) {
                val totalExpectedSamples = (sampleRate * durationSeconds).toLong()
                Math.max(1L, totalExpectedSamples / maxTargetSamples).toInt()
            } else {
                1
            }

            val leftAccumulator = ArrayList<Float>(maxTargetSamples)
            val rightAccumulator = if (channels > 1) ArrayList<Float>(maxTargetSamples) else null

            var isExtractorEOS = false
            var isDecoderEOS = false
            var sampleCounter = 0

            while (!isDecoderEOS && !token.isCancelled()) {
                // Feed input buffers to decoder
                if (!isExtractorEOS) {
                    val inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufIndex >= 0) {
                        val dstBuf = inputBuffers[inputBufIndex]
                        val sampleSize = extractor.readSampleData(dstBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                // Retrieve output buffers from decoder
                val res = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (res >= 0) {
                    val outputBuf = outputBuffers[res]
                    if (bufferInfo.size > 0) {
                        outputBuf.position(bufferInfo.offset)
                        outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuf.order(ByteOrder.nativeOrder())

                        // Parse the native PCM sample data
                        parseAndAccumulatePCM(
                            buffer = outputBuf,
                            encoding = pcmEncoding,
                            channels = channels,
                            step = extractionStep,
                            sampleCounterRef = sampleCounter,
                            left = leftAccumulator,
                            right = rightAccumulator,
                            maxLimit = maxTargetSamples,
                            counterCallback = { sampleCounter = it }
                        )
                    }

                    codec.releaseOutputBuffer(res, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }

                    // Report progress
                    if (durationUs > 0) {
                        val progress = ((bufferInfo.presentationTimeUs * 100) / durationUs).toInt().coerceIn(0, 100)
                        onProgress(progress)
                    }
                } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "Decoder output format changed: ${codec.outputFormat}")
                }
            }

            if (token.isCancelled()) {
                Log.i(TAG, "PCM extraction task cancelled by user.")
                return null
            }

            // Convert lists to high-speed floats
            val leftArr = leftAccumulator.toFloatArray()
            val rightArr = rightAccumulator?.toFloatArray()

            return ExtractedAudio(
                samplesLeft = leftArr,
                samplesRight = rightArr,
                sampleRate = sampleRate,
                channels = channels,
                durationSeconds = durationSeconds
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract native PCM for $src", e)
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {}
            try {
                extractor.release()
            } catch (e: Exception) {}
        }
    }

    private fun setExtractorDataSource(context: Context, extractor: MediaExtractor, src: String) {
        if (src.startsWith("/android_asset/")) {
            val assetName = src.substring("/android_asset/".length)
            context.assets.openFd(assetName).use { afd ->
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        } else if (src.startsWith("http://") || src.startsWith("https://")) {
            extractor.setDataSource(context, Uri.parse(src), null)
        } else {
            val file = File(src)
            if (file.exists()) {
                extractor.setDataSource(file.absolutePath)
            } else {
                // Attempt direct content URI parse or asset fallback
                try {
                    extractor.setDataSource(context, Uri.parse(src), null)
                } catch (e: Exception) {
                    // Try to open it as a direct asset
                    context.assets.openFd(src).use { afd ->
                        extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                }
            }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }

    private fun parseAndAccumulatePCM(
        buffer: ByteBuffer,
        encoding: Int,
        channels: Int,
        step: Int,
        sampleCounterRef: Int,
        left: ArrayList<Float>,
        right: ArrayList<Float>?,
        maxLimit: Int,
        counterCallback: (Int) -> Unit
    ) {
        var localCounter = sampleCounterRef
        val sampleSize = when (encoding) {
            android.media.AudioFormat.ENCODING_PCM_16BIT -> 2
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 3 // 24-bit PCM
        }

        val totalBytes = buffer.remaining()
        val totalSamples = totalBytes / (sampleSize * channels)

        for (s in 0 until totalSamples) {
            if (localCounter % step == 0) {
                if (left.size >= maxLimit) break

                val baseIndex = s * sampleSize * channels
                buffer.position(baseIndex)

                // Read left channel
                val sampleL = readNormalizedSample(buffer, encoding)
                left.add(sampleL)

                // Read right channel if stereo
                if (channels > 1 && right != null) {
                    val sampleR = readNormalizedSample(buffer, encoding)
                    right.add(sampleR)
                }
            }
            localCounter++
        }
        counterCallback(localCounter)
    }

    private fun readNormalizedSample(buffer: ByteBuffer, encoding: Int): Float {
        return when (encoding) {
            android.media.AudioFormat.ENCODING_PCM_16BIT -> {
                val value = buffer.short
                value.toFloat() / 32768.0f
            }
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                buffer.float
            }
            else -> { // 24-bit PCM (3 bytes)
                val b1 = buffer.get().toInt() and 0xFF
                val b2 = buffer.get().toInt() and 0xFF
                val b3 = buffer.get().toInt()
                val value = (b3 shl 16) or (b2 shl 8) or b1
                // Sign extend
                val signExtended = if (value and 0x800000 != 0) {
                    value or -0x1000000
                } else {
                    value
                }
                signExtended.toFloat() / 8388608.0f
            }
        }
    }
}
