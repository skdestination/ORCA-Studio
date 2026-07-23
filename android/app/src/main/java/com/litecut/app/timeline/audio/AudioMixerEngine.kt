package com.litecut.app.timeline.audio

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.PlaybackClock
import com.litecut.app.timeline.TimelineEngine
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The central Native Audio Mixer Engine of ORCA.
 * Operates as a high-performance thread-safe singleton, coordinating active sessions,
 * real-time background rendering, bus structures, and diagnostics.
 * Directly integrates with TimelineEngine, PlaybackClock, ResourceManager, and ExportEngine.
 */
class AudioMixerEngine private constructor(context: Context?) {

    private var appContext = context?.applicationContext
    private val timelineEngine = TimelineEngine.getInstance()
    private val resourceManager = ResourceManager.getInstance(appContext)

    // Clocks and sync
    private val clock = PlaybackClock()
    private val synchronizer = AudioSynchronizer(clock)
    private val metrics = AudioMetrics()

    // Core Pipeline
    private val pipeline = AudioRenderPipeline(timelineEngine, metrics)

    // Active session
    @Volatile
    private var activeSession: AudioMixerSession? = null

    companion object {
        @Volatile
        private var instance: AudioMixerEngine? = null

        /**
         * Returns the thread-safe singleton instance of the AudioMixerEngine.
         * Safe fallback for standalone contexts if null context is passed.
         */
        fun getInstance(context: Context? = null): AudioMixerEngine {
            val ctx = context?.applicationContext ?: com.litecut.app.timeline.ApplicationContextProvider.context
            return instance?.apply {
                if (ctx != null && this.appContext == null) {
                    this.appContext = ctx
                }
            } ?: synchronized(this) {
                instance ?: AudioMixerEngine(ctx).also { instance = it }
            }
        }
    }

    init {
        // Register core audio pools/caches under ResourceManager for unified budget tracking
        resourceManager.registerCache(AudioBufferPool.getInstance().categoryName, AudioBufferPool.getInstance())
        resourceManager.registerCache(AudioCache.getInstance().categoryName, AudioCache.getInstance())

        // Start default mixing session automatically
        startSession()
        Log.i("AudioMixerEngine", "Central Native Audio Mixer Engine successfully initialized.")
    }

    /**
     * Starts and registers a new active audio mixing session.
     */
    fun startSession(): AudioMixerSession {
        synchronized(this) {
            closeSession() // Safely recycle previous sessions
            val session = AudioMixerSession("session-${System.nanoTime()}", resourceManager)
            activeSession = session
            Log.i("AudioMixerEngine", "Active AudioMixerSession initialized: ${session.id}")
            return session
        }
    }

    /**
     * Closes the active session and reclaims all pooled rendering allocations.
     */
    fun closeSession() {
        synchronized(this) {
            activeSession?.let {
                it.release()
                resourceManager.unregisterCache(it.categoryName)
            }
            activeSession = null
        }
    }

    fun getActiveSession(): AudioMixerSession? {
        if (activeSession == null) {
            startSession()
        }
        return activeSession
    }

    fun getTrackForClip(clipId: String): AudioTrack? {
        return getActiveSession()?.getTrackForClip(clipId)
    }

    // --- High-Performance Mixing & Frame Tick Feeds ---

    /**
     * Executes a complete mixing pass at the specified timeline time.
     * Evaluates all buses, active channels, fades, and automations with zero heap allocations.
     * Thread-safe; suitable for background render loops.
     */
    fun mixNextChunk(currentTimeSeconds: Double): AudioMixResult {
        val session = getActiveSession() ?: startSession()
        
        // Mix all active channels routed into parent buses
        return pipeline.render(
            currentTimeSeconds = currentTimeSeconds,
            tracks = session.getAllTracks(),
            buses = session.getAllBuses()
        )
    }

    /**
     * Renders the mixed audio block at the specified timeline time and returns raw 16-bit Stereo PCM bytes
     * (4 bytes per sample frame). Perfect for Direct feeding into ExportEngine's AAC encoder.
     */
    fun mixNextChunkBytes(currentTimeSeconds: Double): ByteArray {
        val mixResult = mixNextChunk(currentTimeSeconds)
        val buffer = mixResult.buffer
        
        if (buffer == null) {
            val empty = ByteArray(512 * 4) // 512 samples * 2 channels * 2 bytes (16-bit)
            AudioMixResult.release(mixResult)
            return empty
        }

        val len = buffer.size
        val bytes = ByteArray(len * 2 * 2) // Stereo (2) * 16-bit depth (2 bytes)

        for (i in 0 until len) {
            val leftVal = (buffer.leftChannel[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt()
            val rightVal = (buffer.rightChannel[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt()

            val idx = i * 4
            // Convert to 16-bit Little-Endian
            bytes[idx] = (leftVal and 0xFF).toByte()
            bytes[idx + 1] = ((leftVal shr 8) and 0xFF).toByte()
            bytes[idx + 2] = (rightVal and 0xFF).toByte()
            bytes[idx + 3] = ((rightVal shr 8) and 0xFF).toByte()
        }

        AudioMixResult.release(mixResult)
        return bytes
    }

    // --- Diagnostics Accessors ---

    fun getMetrics(): AudioMetrics = metrics

    fun shutdown() {
        synchronized(this) {
            closeSession()
            metrics.reset()
            Log.i("AudioMixerEngine", "AudioMixerEngine successfully shut down.")
        }
    }
}
