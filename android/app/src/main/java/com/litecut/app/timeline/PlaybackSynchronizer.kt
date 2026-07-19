package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class PlaybackSynchronizer(
    private val context: Context,
    private val timelineEngine: TimelineEngine
) {
    val clock = PlaybackClock()
    val metrics = PlaybackMetrics()
    private val dropController = FrameDropController(metrics)
    
    var state: PlaybackState = PlaybackState.STOPPED
        private set(value) {
            if (field != value) {
                field = value
                notifyStateChanged(value)
            }
        }

    private val listeners = CopyOnWriteArrayList<PlaybackSyncListener>()
    
    // Asynchronous seek controller
    private val seekController = SeekController(context, metrics) { targetSeconds ->
        clock.setTime(targetSeconds)
        notifyTimeUpdated(targetSeconds, isScrubbing = false)
        notifySeekCompleted(targetSeconds)
    }

    // Vsync-based frame scheduler linked to Choreographer
    private val frameScheduler = FrameScheduler { frameTimeNanos, deltaSeconds ->
        onFrameTick(frameTimeNanos, deltaSeconds)
    }

    init {
        // Match duration to timeline
        clock.setMaxDuration(timelineEngine.getTotalDurationSeconds())
    }

    fun registerListener(listener: PlaybackSyncListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: PlaybackSyncListener) {
        listeners.remove(listener)
    }

    /**
     * Start high-precision playback.
     */
    fun play() {
        if (state == PlaybackState.PLAYING) return
        
        // Ensure duration is updated
        clock.setMaxDuration(timelineEngine.getTotalDurationSeconds())
        
        state = PlaybackState.PLAYING
        clock.start(clock.getTimeSeconds())
        frameScheduler.start()
    }

    /**
     * Pause playback.
     */
    fun pause() {
        if (state != PlaybackState.PLAYING) return
        
        state = PlaybackState.PAUSED
        clock.pause()
        frameScheduler.stop()
    }

    /**
     * Stop and reset playback to zero.
     */
    fun stop() {
        state = PlaybackState.STOPPED
        clock.stop()
        frameScheduler.stop()
        seek(0.0)
    }

    /**
     * Seek instantly to a target time in seconds.
     */
    fun seek(seconds: Double) {
        state = PlaybackState.SEEKING
        notifySeekStarted(seconds)
        seekController.requestSeek(seconds, isScrubbing = false)
    }

    /**
     * Set variable speed (e.g. 2.0x, -1.0x reverse).
     */
    fun setPlaybackSpeed(speed: Double) {
        clock.setSpeed(speed)
    }

    /**
     * Scrubbing interface for high-performance interactive dragging.
     */
    fun startScrubbing() {
        pause()
        state = PlaybackState.SEEKING
    }

    fun scrubTo(seconds: Double) {
        notifySeekStarted(seconds)
        seekController.requestSeek(seconds, isScrubbing = true)
    }

    fun stopScrubbing() {
        if (state == PlaybackState.SEEKING) {
            state = PlaybackState.PAUSED
        }
    }

    /**
     * Continuous audio-video synchronization reference callback.
     * Keeps video timing locked to the hardware audio output clock.
     */
    fun updateAudioTime(audioSeconds: Double) {
        if (state != PlaybackState.PLAYING) return

        val clockTime = clock.getTimeSeconds()
        val drift = audioSeconds - clockTime
        metrics.recordAVDrift((drift * 1000).toLong())

        // If drift is within 1 frame (e.g., 33ms at 30 FPS), apply soft micro-correction
        if (abs(drift) > 0.005 && abs(drift) < 0.1) {
            // Apply a proportional correction (soft alignment)
            clock.applyDriftCorrection(drift * 0.5)
        } else if (abs(drift) >= 0.1) {
            // Hard jump to audio time if major drift/stall detected
            clock.setTime(audioSeconds)
        }
    }

    /**
     * Frame scheduler tick callback. Evaluates active animatable properties
     * on clips at current clock time, enforces frame drops, and schedules presentation.
     */
    private fun onFrameTick(frameTimeNanos: Long, deltaSeconds: Double) {
        val startTimeNs = System.nanoTime()
        val currentTime = clock.getTimeSeconds()

        // Loop play support
        if (currentTime >= timelineEngine.getTotalDurationSeconds()) {
            if (clock.getSpeed() > 0) {
                seek(0.0)
                return
            }
        }

        // Check for frame drops using our Drop Controller
        val frameDuration = 1.0 / clock.getSpeed() // dynamic frame rate correction
        val decision = dropController.evaluateFrame(currentTime, currentTime, 1.0 / 30.0)

        if (decision == FrameDropController.DropDecision.DROP) {
            notifyFrameDropped(currentTime, currentTime)
            return
        }

        // Evaluate animatable keyframes for all visible clips
        val clips = timelineEngine.getAllClips()
        for (clip in clips) {
            if (currentTime >= clip.leftSeconds && currentTime <= clip.leftSeconds + clip.durationSeconds) {
                val relativeOffset = currentTime - clip.leftSeconds
                // Force-pre-evaluate major channels to keep hot paths zero-alloc
                AnimationEvaluator.evaluate(clip, "opacity", relativeOffset)
                AnimationEvaluator.evaluate(clip, "scale_x", relativeOffset)
                AnimationEvaluator.evaluate(clip, "scale_y", relativeOffset)
            }
        }

        // Dispatch updated frame callback to listeners (like TimelineRenderer and View)
        notifyTimeUpdated(currentTime, isScrubbing = false)

        // Record performance metrics
        val renderTimeNs = System.nanoTime() - startTimeNs
        metrics.recordFrameRendered(renderTimeNs)
    }

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

    private fun notifyFrameDropped(targetTime: Double, actualTime: Double) {
        for (listener in listeners) {
            listener.onFrameDropped(targetTime, actualTime)
        }
    }

    fun shutdown() {
        frameScheduler.stop()
        seekController.cancelAll()
        listeners.clear()
        clock.reset()
        metrics.reset()
        dropController.reset()
    }
}
