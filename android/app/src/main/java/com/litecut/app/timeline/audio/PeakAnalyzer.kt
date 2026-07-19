package com.litecut.app.timeline.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Converts PCM samples into normalized peak amplitude arrays.
 * Implements high-end DAW peak extraction with noise-floor normalization,
 * root-mean-square (RMS) level calculation, and silence/loudness analysis.
 */
object PeakAnalyzer {

    class AnalysisResult(
        val peaksLeft: FloatArray,
        val peaksRight: FloatArray?,
        val avgLoudnessDb: Float,
        val volumeEnvelope: FloatArray,
        val isSilence: Boolean
    )

    /**
     * Analyzes raw PCM floats to extract peak-preserved amplitudes.
     */
    fun analyze(
        samplesLeft: FloatArray,
        samplesRight: FloatArray?,
        targetSize: Int
    ): AnalysisResult {
        if (samplesLeft.isEmpty()) {
            return AnalysisResult(
                peaksLeft = FloatArray(targetSize) { 0.05f },
                peaksRight = if (samplesRight != null) FloatArray(targetSize) { 0.05f } else null,
                avgLoudnessDb = -100f,
                volumeEnvelope = FloatArray(targetSize) { 0.05f },
                isSilence = true
            )
        }

        val peaksL = FloatArray(targetSize)
        val peaksR = if (samplesRight != null) FloatArray(targetSize) else null
        val envelope = FloatArray(targetSize)

        // Slicing windows
        val factorL = samplesLeft.size.toDouble() / targetSize
        val factorR = samplesRight?.let { it.size.toDouble() / targetSize } ?: 0.0

        var sumSqLeft = 0.0
        var totalCount = 0

        var globalMaxL = 0.01f
        var globalMaxR = 0.01f

        // Compute peak amplitudes within windows
        for (i in 0 until targetSize) {
            val startIdxL = (i * factorL).toInt().coerceIn(0, samplesLeft.size - 1)
            val endIdxL = ((i + 1) * factorL).toInt().coerceIn(0, samplesLeft.size - 1)

            var maxValL = 0.0f
            var sumSqWindow = 0.0
            val countL = (endIdxL - startIdxL + 1)

            for (j in startIdxL..endIdxL) {
                val absVal = abs(samplesLeft[j])
                if (absVal > maxValL) {
                    maxValL = absVal
                }
                sumSqWindow += absVal * absVal
            }
            peaksL[i] = maxValL
            if (maxValL > globalMaxL) {
                globalMaxL = maxValL
            }

            sumSqLeft += sumSqWindow
            totalCount += countL

            // Envelope calculation (RMS-based window volume)
            val rmsWindow = if (countL > 0) sqrt(sumSqWindow / countL).toFloat() else 0f
            envelope[i] = rmsWindow

            // Process right channel if available
            if (peaksR != null && samplesRight != null) {
                val startIdxR = (i * factorR).toInt().coerceIn(0, samplesRight.size - 1)
                val endIdxR = ((i + 1) * factorR).toInt().coerceIn(0, samplesRight.size - 1)
                var maxValR = 0.0f
                for (j in startIdxR..endIdxR) {
                    val absVal = abs(samplesRight[j])
                    if (absVal > maxValR) {
                        maxValR = absVal
                    }
                }
                peaksR[i] = maxValR
                if (maxValR > globalMaxR) {
                    globalMaxR = maxValR
                }
            }
        }

        // Calculate average loudness in dBFS
        val rmsOverall = if (totalCount > 0) sqrt(sumSqLeft / totalCount) else 0.0
        val avgLoudnessDb = if (rmsOverall > 0.0) (20.0 * log10(rmsOverall)).toFloat() else -100f
        val isSilence = avgLoudnessDb < -55.0f // -55dBFS or lower is treated as silence

        // Normalize while preserving relative dynamic range (Standard DAW mastering compressor curves)
        val targetPeak = 0.96f
        val scaleL = if (globalMaxL > 0.01f) targetPeak / globalMaxL else 1.0f
        val scaleR = if (globalMaxR > 0.01f) targetPeak / globalMaxR else 1.0f

        // Apply visual compressor curve: boost very quiet parts slightly so waveform is readable,
        // but scale back louder sections to prevent clipping.
        for (i in 0 until targetSize) {
            peaksL[i] = compressVisualRange(peaksL[i] * scaleL)
            if (peaksR != null) {
                peaksR[i] = compressVisualRange(peaksR[i] * scaleR)
            }
            envelope[i] = (envelope[i] * scaleL).coerceIn(0.01f, 1.0f)
        }

        return AnalysisResult(
            peaksLeft = peaksL,
            peaksRight = peaksR,
            avgLoudnessDb = avgLoudnessDb,
            volumeEnvelope = envelope,
            isSilence = isSilence
        )
    }

    /**
     * Soft-knee compressor curve helper.
     * Prevents tiny quiet audio lines from becoming invisible, and limits high peaks gracefully.
     */
    private fun compressVisualRange(valIn: Float): Float {
        val x = valIn.coerceIn(0f, 1.2f)
        return if (x < 0.15f) {
            // Slight boost for very quiet parts
            (x * 1.5f).coerceIn(0.03f, 0.22f)
        } else if (x > 0.75f) {
            // Soft limiting for peaks
            0.75f + (x - 0.75f) * 0.4f
        } else {
            x
        }.coerceIn(0.01f, 1.0f)
    }
}
