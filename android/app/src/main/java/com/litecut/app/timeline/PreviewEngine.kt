package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The central, production-grade playback coordinator of ORCA.
 * Seamlessly integrates TimelineEngine, PlaybackClock, SeekController, PreviewScheduler,
 * CompositionEngine, OpenGL RenderPipeline, and all modular rendering engines.
 * Acts as the single high-performance, thread-safe entry point for all preview operations,
 * supporting high frame rates up to 120 FPS at 4K HDR.
 */
class PreviewEngine private constructor(private var context: Context?) : PreviewSchedulerListener {

    private val timelineEngine = TimelineEngine.getInstance()
    private val renderPipeline = RenderPipeline.getInstance()
    
    // Playback clocks and performance metrics
    private val clock = PlaybackClock()
    private val metrics = PlaybackMetrics()
    
    // Active session and scheduler instances
    private var activeSession: PreviewSession? = null
    private var scheduler: PreviewScheduler? = null
    
    // Seek controller for non-blocking asynchronous scrubbing and seeks
    private var seekController: SeekController? = null

    // Playback and scrubbing states
    @Volatile
    private var state: PlaybackState = PlaybackState.STOPPED
    
    private val listeners = CopyOnWriteArrayList<PlaybackSyncListener>()

    companion object {
        @Volatile
        private var instance: PreviewEngine? = null

        fun getInstance(context: Context? = null): PreviewEngine {
            val ctx = context?.applicationContext
            return instance?.apply {
                if (ctx != null && this.context == null) {
                    this.context = ctx
                }
            } ?: synchronized(this) {
                instance ?: PreviewEngine(ctx).also { instance = it }
            }
        }
    }

    init {
        // Initialize seek controller to draw frames on the GPU instantly
        seekController = SeekController(context, metrics) { targetSeconds ->
            clock.setTime(targetSeconds)
            
            // Frame-accurate rendering during seek/scrub operations
            renderSingleFrameImmediate(targetSeconds)
            
            notifyTimeUpdated(targetSeconds, isScrubbing = (state == PlaybackState.SEEKING))
            notifySeekCompleted(targetSeconds)
        }

        // Initialize default scheduler at 60 FPS
        scheduler = PreviewScheduler(clock, this)
        scheduler?.setTargetFps(60.0)

        Log.i("PreviewEngine", "Central Native Preview Engine successfully initialized.")
    }

    /**
     * Spawns and activates a new high-performance editing and rendering session.
     */
    fun startSession(): PreviewSession {
        synchronized(this) {
            closeSession() // Safe reset any previous sessions
            
            val session = PreviewSession(context, timelineEngine, renderPipeline)
            activeSession = session
            clock.setMaxDuration(timelineEngine.getTotalDurationSeconds())
            
            Log.i("PreviewEngine", "Active PreviewSession opened with ID: ${session.id}")
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
                // Unregister from ResourceManager
                ResourceManager.getInstance(context).clearAll()
            }
            activeSession = null
        }
    }

    fun getActiveSession(): PreviewSession? = activeSession

    // --- Playback Sync Listener Registration ---

    fun registerListener(listener: PlaybackSyncListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: PlaybackSyncListener) {
        listeners.remove(listener)
    }

    // --- Central Playback Controls ---

    /**
     * Start high-fidelity vsync-synchronized playback.
     */
    fun play() {
        synchronized(this) {
            if (state == PlaybackState.PLAYING) return
            
            val session = activeSession ?: startSession()
            
            // Adjust clock limit with the timeline's active length
            clock.setMaxDuration(timelineEngine.getTotalDurationSeconds())
            
            state = PlaybackState.PLAYING
            notifyStateChanged(PlaybackState.PLAYING)
            
            clock.start(clock.getTimeSeconds())
            scheduler?.start()
            
            Log.d("PreviewEngine", "Playback started at playhead: ${clock.getTimeSeconds()}s")
        }
    }

    /**
     * Pauses the playback stream instantly.
     */
    fun pause() {
        synchronized(this) {
            if (state != PlaybackState.PLAYING) return
            
            state = PlaybackState.PAUSED
            notifyStateChanged(PlaybackState.PAUSED)
            
            clock.pause()
            scheduler?.stop()
            
            Log.d("PreviewEngine", "Playback paused at playhead: ${clock.getTimeSeconds()}s")
        }
    }

    /**
     * Stops the playback stream and resets playhead to 0.0s.
     */
    fun stop() {
        synchronized(this) {
            state = PlaybackState.STOPPED
            notifyStateChanged(PlaybackState.STOPPED)
            
            clock.stop()
            scheduler?.stop()
            
            seek(0.0)
            Log.d("PreviewEngine", "Playback stopped and reset.")
        }
    }

    /**
     * Performs an asynchronous, frame-accurate playhead seek.
     */
    fun seek(seconds: Double) {
        val target = seconds.coerceIn(0.0, timelineEngine.getTotalDurationSeconds())
        notifySeekStarted(target)
        
        val isScrubbing = (state == PlaybackState.SEEKING)
        seekController?.requestSeek(target, isScrubbing)
    }

    /**
     * Increments playhead forward by exactly 1 frame relative to target FPS.
     */
    fun stepFrameForward(stepCount: Int = 1) {
        val fps = 60.0 // Target composition frame rate
        val stepSize = (1.0 / fps) * stepCount
        val target = clock.getTimeSeconds() + stepSize
        seek(target)
    }

    /**
     * Decrements playhead backward by exactly 1 frame.
     */
    fun stepFrameBackward(stepCount: Int = 1) {
        val fps = 60.0
        val stepSize = (1.0 / fps) * stepCount
        val target = clock.getTimeSeconds() - stepSize
        seek(target)
    }

    /**
     * Updates playback speed scaling (supports reverse playback: speed < 0).
     */
    fun setPlaybackSpeed(speed: Double) {
        synchronized(this) {
            clock.setSpeed(speed)
            Log.d("PreviewEngine", "Playback speed updated to: ${speed}x")
        }
    }

    fun getPlaybackSpeed(): Double = clock.getSpeed()

    fun isPlaying(): Boolean = state == PlaybackState.PLAYING

    fun getCurrentTimeSeconds(): Double = clock.getTimeSeconds()

    // --- Interactive Scrubbing Interfaces ---

    fun startScrubbing() {
        synchronized(this) {
            pause()
            state = PlaybackState.SEEKING
            notifyStateChanged(PlaybackState.SEEKING)
        }
    }

    fun scrubTo(seconds: Double) {
        seek(seconds)
    }

    fun stopScrubbing() {
        synchronized(this) {
            if (state == PlaybackState.SEEKING) {
                state = PlaybackState.PAUSED
                notifyStateChanged(PlaybackState.PAUSED)
            }
        }
    }

    // --- Invalidation and Quality Controls ---

    /**
     * Force-invalidates the current session's dirty state (call on timeline changes).
     */
    fun invalidate() {
        activeSession?.invalidate()
    }

    fun setProxyMode(enabled: Boolean) {
        synchronized(this) {
            activeSession?.isProxyMode = enabled
            activeSession?.invalidate()
            Log.d("PreviewEngine", "Proxy Mode set to: $enabled")
        }
    }

    fun setHDR(enabled: Boolean) {
        synchronized(this) {
            activeSession?.isHDR = enabled
            activeSession?.invalidate()
            Log.d("PreviewEngine", "HDR set to: $enabled")
        }
    }

    fun setTargetFps(fps: Double) {
        scheduler?.setTargetFps(fps)
    }

    // --- PreviewSchedulerListener Integration ---

    /**
     * Main vsync ticking loop. Pulls composed frames and issues high-performance
     * OpenGL rendering calls on every screen refresh cycle.
     */
    override fun onFrameTick(frameTimeNanos: Long, deltaSeconds: Double, currentSeconds: Double) {
        val session = activeSession ?: return
        val schedulerInstance = scheduler ?: return
        
        // Handle Loop Playback bounds
        val maxDuration = timelineEngine.getTotalDurationSeconds()
        if (currentSeconds >= maxDuration) {
            if (clock.getSpeed() > 0) {
                seek(0.0)
                return
            }
        } else if (currentSeconds < 0.0) {
            if (clock.getSpeed() < 0) {
                seek(maxDuration)
                return
            }
        }

        // 1. Compile active frame at the target adaptive scale
        val qualityScale = schedulerInstance.adaptiveQualityScale
        val frame = session.composeFrame(currentSeconds, qualityScale)
        
        // 2. Submit composed frame to the OpenGL Render Pipeline
        val stats = renderPipeline.renderFrame(frame.compositionOutput)
        
        // 3. Log render execution metrics
        metrics.recordFrameRendered(stats.lastFrameRenderTimeNs)
        
        // 4. Dispatch position change updates to listeners
        notifyTimeUpdated(currentSeconds, isScrubbing = false)
        
        // 5. Recycle frame descriptor instantly
        PreviewFrame.release(frame)
    }

    /**
     * Force-renders a single high-quality frame immediately on the GPU.
     * Useful for instantaneous scrubbing or seek state changes.
     */
    private fun renderSingleFrameImmediate(seconds: Double) {
        val session = activeSession ?: startSession()
        
        // Force full resolution quality during scrubbing/seeks to guarantee visual crispness
        val frame = session.composeFrame(seconds, 1.0f)
        renderPipeline.renderFrame(frame.compositionOutput)
        PreviewFrame.release(frame)
    }

    // --- Synchronized Listener Notification Dispatchers ---

    private fun notifyStateChanged(newState: PlaybackState) {
        for (listener in listeners) {
            listener.onStateChanged(newState)
        }
    }

    private fun notifyTimeUpdated(seconds: Double, isScrubbing: Boolean) {
        for (listener in listeners) {
            listener.onTimeUpdated(seconds, isScrubbing)
        }
    }

    private fun notifySeekStarted(seconds: Double) {
        for (listener in listeners) {
            listener.onSeekStarted(seconds)
        }
    }

    private fun notifySeekCompleted(seconds: Double) {
        for (listener in listeners) {
            listener.onSeekCompleted(seconds)
        }
    }

    fun shutdown() {
        synchronized(this) {
            scheduler?.stop()
            seekController?.cancelAll()
            closeSession()
            listeners.clear()
            clock.reset()
            metrics.reset()
            state = PlaybackState.STOPPED
            Log.i("PreviewEngine", "PreviewEngine shutdown complete.")
        }
    }
}
