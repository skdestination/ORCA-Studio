package com.litecut.app.timeline.audio

import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents an active, thread-safe professional mixing session.
 * Stores console tracks, bus structures, routing paths, and coordinates memory management
 * via the ResourceManager.
 */
class AudioMixerSession(
    val id: String,
    private val resourceManager: ResourceManager?
) : ManagedCache {

    override val categoryName: String = "audio_mixer_session_$id"

    // Virtual Tracks mapping (Key: Track ID / Layer ID, Value: AudioTrack)
    private val tracks = ConcurrentHashMap<String, AudioTrack>()

    // Mixing buses mapping (MASTER, MUSIC, SFX, VOICE)
    private val buses = ConcurrentHashMap<String, AudioBus>()

    init {
        setupDefaultBuses()
        resourceManager?.registerCache(categoryName, this)
    }

    private fun setupDefaultBuses() {
        buses["MASTER"] = AudioBus("MASTER", BusType.MASTER)
        buses["MUSIC"] = AudioBus("MUSIC", BusType.MUSIC)
        buses["VOICE"] = AudioBus("VOICE", BusType.VOICE)
        buses["SFX"] = AudioBus("SFX", BusType.SFX)
    }

    fun getTrack(trackId: String, name: String, type: BusType = BusType.SFX): AudioTrack {
        return tracks.getOrPut(trackId) {
            AudioTrack(trackId, name, type)
        }
    }

    fun getTrackForClip(clipId: String): AudioTrack? {
        return tracks.values.find { it.getAllChannels().any { ch -> ch.id == clipId } }
    }

    fun getAllTracks(): Collection<AudioTrack> = tracks.values

    fun getBus(busId: String): AudioBus? = buses[busId]

    fun getAllBuses(): Map<String, AudioBus> = buses

    override fun getCurrentSizeBytes(): Long {
        // Simple sizing estimation: 512 bytes per virtual track entry
        return tracks.size * 512L + buses.size * 256L
    }

    override fun trimMemory(bytesToFree: Long) {
        // Trim inactive tracks or channel states under memory pressure
        synchronized(this) {
            tracks.values.forEach { it.reset() }
            tracks.clear()
            setupDefaultBuses()
        }
    }

    override fun clear() {
        synchronized(this) {
            tracks.clear()
            buses.clear()
            setupDefaultBuses()
        }
    }

    fun release() {
        clear()
    }
}
