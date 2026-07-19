package com.litecut.app.timeline.audio

import java.util.concurrent.ConcurrentHashMap

/**
 * Models a master or virtual Audio Track on our console, routing elements to a parent BusType.
 * Manages active channel strips mapped to individual timeline audio clips.
 */
class AudioTrack(
    val id: String,
    var name: String,
    var type: BusType = BusType.SFX
) {
    var isMuted: Boolean = false
    var isSolo: Boolean = false
    var volume: Float = 1.0f

    // Mapped channels (Key: Clip ID, Value: MixerChannel)
    private val channels = ConcurrentHashMap<String, MixerChannel>()

    fun getChannel(clipId: String): MixerChannel {
        return channels.getOrPut(clipId) {
            val channel = MixerChannel(clipId)
            channel.targetBusId = type.name
            channel
        }
    }

    fun removeChannel(clipId: String) {
        channels.remove(clipId)
    }

    fun getAllChannels(): Collection<MixerChannel> {
        return channels.values
    }

    fun reset() {
        isMuted = false
        isSolo = false
        volume = 1.0f
        channels.values.forEach { it.reset() }
        channels.clear()
    }
}
