package com.litecut.app.timeline.audio

/**
 * Standard bus routing designations within ORCA's mixing console.
 */
enum class BusType {
    MASTER, MUSIC, VOICE, SFX, CUSTOM
}

/**
 * Models an Audio Mixing Bus. Tracks routing, volume levels, panning, gains, 
 * and stereo attributes for Master, Music, Voice, and SFX channels.
 */
class AudioBus(
    val id: String,
    val type: BusType,
    var name: String = type.name
) {
    var volume: Float = 1.0f      // 0.0f to 1.0f (or custom decibel conversions)
    var gain: Float = 1.0f        // Pre-fader multiplication
    var pan: Float = 0.0f         // -1.0f (Full Left) to 1.0f (Full Right)
    var isMuted: Boolean = false
    var isSolo: Boolean = false
    
    // Future-ready processing toggles
    var isEQEnabled: Boolean = false
    var isLimiterEnabled: Boolean = false

    // Direct routing to target (usually MASTER)
    var outputBusId: String? = if (type == BusType.MASTER) null else "MASTER"

    fun reset() {
        volume = 1.0f
        gain = 1.0f
        pan = 0.0f
        isMuted = false
        isSolo = false
    }

    /**
     * Applies volume, panning, and gain adjustments directly on an AudioBuffer in-place.
     * Zero-allocation.
     */
    fun process(buffer: AudioBuffer) {
        if (isMuted || volume == 0.0f) {
            buffer.leftChannel.fill(0.0f)
            buffer.rightChannel.fill(0.0f)
            return
        }

        val finalGain = gain * volume
        val panLeftFactor: Float
        val panRightFactor: Float

        if (pan < 0f) {
            panLeftFactor = 1.0f
            panRightFactor = 1.0f + pan // pan is negative, so decreases right channel
        } else {
            panLeftFactor = 1.0f - pan
            panRightFactor = 1.0f
        }

        val finalLeft = finalGain * panLeftFactor
        val finalRight = finalGain * panRightFactor

        val len = buffer.size
        for (i in 0 until len) {
            buffer.leftChannel[i] *= finalLeft
            buffer.rightChannel[i] *= finalRight
        }
    }
}
