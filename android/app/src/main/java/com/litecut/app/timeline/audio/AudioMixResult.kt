package com.litecut.app.timeline.audio

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Clean, zero-allocation container that stores mixed audio output
 * along with real-time level monitoring values (RMS, Peak, and Clipping status).
 */
class AudioMixResult private constructor() {

    // Final mixed PCM buffers (leased from AudioBufferPool internally)
    var buffer: AudioBuffer? = null
        private set

    var peakLeft: Float = 0.0f
    var peakRight: Float = 0.0f
    var rmsLeft: Float = 0.0f
    var rmsRight: Float = 0.0f
    var isClipping: Boolean = false

    companion object {
        private val pool = java.util.concurrent.ConcurrentLinkedQueue<AudioMixResult>()

        /**
         * Leases a clean AudioMixResult from our thread-safe pool.
         */
        fun obtain(sourceBuffer: AudioBuffer): AudioMixResult {
            val result = pool.poll() ?: AudioMixResult()
            result.buffer = sourceBuffer
            result.calculateMetrics()
            return result
        }

        /**
         * Recycles the AudioMixResult and returns its leased buffer to the buffer pool.
         */
        fun release(result: AudioMixResult) {
            result.buffer?.let {
                AudioBufferPool.getInstance().release(it)
            }
            result.reset()
            pool.offer(result)
        }
    }

    private fun calculateMetrics() {
        val buf = buffer ?: return
        val len = buf.size
        if (len == 0) {
            peakLeft = 0.0f
            peakRight = 0.0f
            rmsLeft = 0.0f
            rmsRight = 0.0f
            isClipping = false
            return
        }

        var sumSquareLeft = 0.0f
        var sumSquareRight = 0.0f
        var maxLeft = 0.0f
        var maxRight = 0.0f
        var clipping = false

        for (i in 0 until len) {
            val l = buf.leftChannel[i]
            val r = buf.rightChannel[i]

            val absL = kotlin.math.abs(l)
            val absR = kotlin.math.abs(r)

            if (absL > maxLeft) maxLeft = absL
            if (absR > maxRight) maxRight = absR

            if (absL > 1.0f || absR > 1.0f) {
                clipping = true
            }

            sumSquareLeft += l * l
            sumSquareRight += r * r
        }

        peakLeft = maxLeft
        peakRight = maxRight
        rmsLeft = sqrt(sumSquareLeft / len)
        rmsRight = sqrt(sumSquareRight / len)
        isClipping = clipping
    }

    private fun reset() {
        buffer = null
        peakLeft = 0.0f
        peakRight = 0.0f
        rmsLeft = 0.0f
        rmsRight = 0.0f
        isClipping = false
    }
}
