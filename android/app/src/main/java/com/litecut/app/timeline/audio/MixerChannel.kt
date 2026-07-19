package com.litecut.app.timeline.audio

import com.litecut.app.timeline.AnimationEvaluator
import com.litecut.app.timeline.Clip
import kotlin.math.max
import kotlin.math.min

/**
 * Represents the professional processing strip for a single audio source.
 * Handles volume, gain, pan, balance, stereo width, fade in/out envelopes,
 * and keyframe automation.
 * Evaluates everything using zero-allocation reusable structures in the audio loop.
 */
class MixerChannel(
    val id: String, // Usually maps to a TimelineClip (Clip) ID
    var name: String = "Channel-$id"
) {
    // Static / fallback configuration
    var volume: Float = 1.0f
    var pan: Float = 0.0f // -1f (Full Left) to 1f (Full Right)
    var gain: Float = 1.0f // Input pre-gain (e.g. trimming source file loudness)
    var balance: Float = 0.0f // Stereo balance
    var stereoWidth: Float = 1.0f // 0f (Mono) to 2f (Wide Stereo)
    var playbackSpeed: Double = 1.0
    var pitch: Float = 1.0f
    var audioOffsetSeconds: Double = 0.0

    // Fade configuration
    var fadeInDuration: Double = 0.0
    var fadeOutDuration: Double = 0.0

    var isMuted: Boolean = false
    var isSolo: Boolean = false

    // Routing destination bus ID (MASTER, MUSIC, VOICE, SFX, or custom)
    var targetBusId: String = "MASTER"

    fun reset() {
        volume = 1.0f
        pan = 0.0f
        gain = 1.0f
        balance = 0.0f
        stereoWidth = 1.0f
        playbackSpeed = 1.0
        pitch = 1.0f
        audioOffsetSeconds = 0.0
        fadeInDuration = 0.0
        fadeOutDuration = 0.0
        isMuted = false
        isSolo = false
        targetBusId = "MASTER"
    }

    /**
     * Evaluates automated keyframes and applies fades, volume adjustments, 
     * panning, and width to the leased buffer.
     * Zero-allocation.
     */
    fun process(
        clip: Clip,
        relativeTimeSeconds: Double,
        clipDurationSeconds: Double,
        buffer: AudioBuffer
    ) {
        // 1. Evaluate keyframe automations
        val finalVolume = evaluateVolume(clip, relativeTimeSeconds)
        val finalPan = evaluatePan(clip, relativeTimeSeconds)
        val finalGain = evaluateGain(clip, relativeTimeSeconds)
        val finalMuted = evaluateMute(clip, relativeTimeSeconds) || isMuted || clip.mute

        if (finalMuted || finalVolume == 0.0f) {
            buffer.leftChannel.fill(0.0f)
            buffer.rightChannel.fill(0.0f)
            return
        }

        // 2. Compute fade envelopes (fade-in, fade-out)
        val fadeFactor = computeFadeEnvelope(relativeTimeSeconds, clipDurationSeconds)

        // 3. Apply volume, pre-gain, fades, panning, balance, and width
        val mixGain = finalGain * finalVolume * fadeFactor

        // Panning math (Constant power panning)
        val panLeftFactor: Float
        val panRightFactor: Float
        if (finalPan < 0f) {
            panLeftFactor = 1.0f
            panRightFactor = 1.0f + finalPan
        } else {
            panLeftFactor = 1.0f - finalPan
            panRightFactor = 1.0f
        }

        // Balance & Stereo Width Math
        val widthLeft = mixGain * panLeftFactor * (1.0f - min(1.0f, max(0.0f, balance)))
        val widthRight = mixGain * panRightFactor * (1.0f - min(1.0f, max(0.0f, -balance)))

        val len = buffer.size
        for (i in 0 until len) {
            val left = buffer.leftChannel[i]
            val right = buffer.rightChannel[i]

            // Apply stereo width synthesis
            val monoSum = (left + right) * 0.5f
            val leftSide = left - monoSum
            val rightSide = right - monoSum

            val processedLeft = (monoSum + leftSide * stereoWidth) * widthLeft
            val processedRight = (monoSum + rightSide * stereoWidth) * widthRight

            buffer.leftChannel[i] = processedLeft
            buffer.rightChannel[i] = processedRight
        }
    }

    /**
     * Calculates the fade multiplier (0.0f - 1.0f) for linear fade envelopes.
     */
    private fun computeFadeEnvelope(relativeTime: Double, duration: Double): Float {
        var envelope = 1.0f
        
        // Apply fade in
        if (fadeInDuration > 0.0 && relativeTime < fadeInDuration) {
            envelope *= (relativeTime / fadeInDuration).toFloat()
        }
        
        // Apply fade out
        if (fadeOutDuration > 0.0 && relativeTime > (duration - fadeOutDuration)) {
            val remaining = duration - relativeTime
            envelope *= (remaining / fadeOutDuration).toFloat()
        }

        return envelope.coerceIn(0.0f, 1.0f)
    }

    // --- Keyframe Automation Evaluators ---

    private fun evaluateVolume(clip: Clip, relativeTime: Double): Float {
        return try {
            AnimationEvaluator.evaluate(clip, "volume", relativeTime).toFloat()
        } catch (e: Exception) {
            volume
        }
    }

    private fun evaluatePan(clip: Clip, relativeTime: Double): Float {
        return try {
            AnimationEvaluator.evaluate(clip, "pan", relativeTime).toFloat()
        } catch (e: Exception) {
            pan
        }
    }

    private fun evaluateGain(clip: Clip, relativeTime: Double): Float {
        return try {
            AnimationEvaluator.evaluate(clip, "gain", relativeTime).toFloat()
        } catch (e: Exception) {
            gain
        }
    }

    private fun evaluateMute(clip: Clip, relativeTime: Double): Boolean {
        return try {
            val muteVal = AnimationEvaluator.evaluate(clip, "mute", relativeTime)
            muteVal > 0.5
        } catch (e: Exception) {
            isMuted
        }
    }

    /**
     * Evaluates custom bus routing parameters or custom sends dynamically.
     */
    fun evaluateBusSend(clip: Clip, relativeTime: Double): String {
        return try {
            // Dynamic routing keyframe checks (e.g. send to other buses)
            val sendVal = AnimationEvaluator.evaluate(clip, "bus_send", relativeTime)
            when (sendVal.toInt()) {
                1 -> "MUSIC"
                2 -> "VOICE"
                3 -> "SFX"
                else -> targetBusId
            }
        } catch (e: Exception) {
            targetBusId
        }
    }
}
