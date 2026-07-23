package com.litecut.app.timeline.audio

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.Clip
import com.litecut.app.timeline.ClipType
import com.litecut.app.timeline.Viewport
import com.litecut.app.timeline.cache.WaveformCache
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import java.util.concurrent.ConcurrentHashMap

enum class AudioTrackType {
    MUSIC, VOICE, SFX, AMBIENT
}

/**
 * Central coordinator for the production-grade Waveform Engine.
 * Manages task scheduling, caching, viewport awareness, memory-budget pinning,
 * and predictive prefetching.
 */
class WaveformEngine private constructor(val context: Context) {

    val cache = WaveformCache(context)
    private val pendingTaskIds = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val TAG = "WaveformEngine"

        @Volatile
        private var instance: WaveformEngine? = null

        fun getInstance(context: Context): WaveformEngine {
            return instance ?: synchronized(this) {
                instance ?: WaveformEngine(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): WaveformEngine {
            return instance ?: throw IllegalStateException("WaveformEngine has not been initialized with Context.")
        }
    }

    /**
     * Retrieves the track classification for a clip.
     * Decides colors dynamically based on clip properties, names, or stable hashes.
     */
    fun getAudioTrackType(clip: Clip): AudioTrackType {
        val typeProp = clip.additionalProperties["audioTrackType"] as? String
        if (typeProp != null) {
            try {
                return AudioTrackType.valueOf(typeProp.uppercase())
            } catch (e: Exception) {}
        }

        val name = (clip.name ?: "").lowercase()
        val src = clip.src.lowercase()
        return when {
            name.contains("music") || name.contains("bgm") || name.contains("song") || src.contains("music") || src.contains("bgm") -> AudioTrackType.MUSIC
            name.contains("voice") || name.contains("dialog") || name.contains("speech") || name.contains("narr") || name.contains("talk") || src.contains("voice") -> AudioTrackType.VOICE
            name.contains("sfx") || name.contains("effect") || name.contains("foley") || name.contains("hit") || name.contains("click") || src.contains("sfx") -> AudioTrackType.SFX
            name.contains("ambient") || name.contains("bg") || name.contains("noise") || name.contains("wind") || name.contains("rain") || src.contains("ambient") -> AudioTrackType.AMBIENT
            else -> {
                val index = Math.abs(clip.id.hashCode()) % AudioTrackType.values().size
                AudioTrackType.values()[index]
            }
        }
    }

    /**
     * Gets the matching color hex for the specified track type.
     */
    fun getTrackColor(type: AudioTrackType): Int {
        return when (type) {
            AudioTrackType.MUSIC -> 0xFF9061F9.toInt()    // Purple
            AudioTrackType.VOICE -> 0xFF06B6D4.toInt()    // Cyan
            AudioTrackType.SFX -> 0xFFF97316.toInt()      // Orange
            AudioTrackType.AMBIENT -> 0xFF10B981.toInt()  // Green
        }
    }

    /**
     * Synchronously checks cache for a clip's waveform. If not cached,
     * submits a background waveform generation task to TaskScheduler.
     */
    fun getOrRequestWaveform(clip: Clip, onComplete: (WaveformData) -> Unit): WaveformData? {
        val cached = cache.getWaveform(clip.id)
        if (cached != null) {
            return cached
        }

        // Avoid triggering multiple tasks for the same clip
        if (pendingTaskIds.add(clip.id)) {
            val task = WaveformWorker(context, clip.id, clip.src, TaskPriority.HIGH)
            val scheduler = TaskScheduler.getInstance(context)
            val handle = scheduler.submit(task)

            val listener = object : com.litecut.app.timeline.tasks.TaskHandle.TaskProgressListener {
                override fun onStateChanged(state: com.litecut.app.timeline.tasks.TaskState) {
                    if (state == com.litecut.app.timeline.tasks.TaskState.COMPLETED) {
                        val result = handle.join()
                        if (result is com.litecut.app.timeline.tasks.TaskResult.Success) {
                            val data = result.value
                            cache.putWaveform(clip.id, data)
                            pendingTaskIds.remove(clip.id)
                            onComplete(data)
                        }
                    } else if (state == com.litecut.app.timeline.tasks.TaskState.FAILED || state == com.litecut.app.timeline.tasks.TaskState.CANCELLED) {
                        pendingTaskIds.remove(clip.id)
                    }
                }

                override fun onProgressUpdated(progress: Int) {}
            }
            handle.addListener(listener)
        }

        return null
    }

    /**
     * Overloaded request helper for programmatic/predictive prefetching
     */
    fun requestWaveformAsynchronously(
        clip: Clip,
        priority: TaskPriority = TaskPriority.HIGH,
        onComplete: (WaveformData) -> Unit
    ) {
        val cached = cache.getWaveform(clip.id)
        if (cached != null) {
            onComplete(cached)
            return
        }

        if (pendingTaskIds.add(clip.id)) {
            val task = WaveformWorker(context, clip.id, clip.src, priority)
            val scheduler = TaskScheduler.getInstance(context)
            val handle = scheduler.submit(task)

            val listener = object : com.litecut.app.timeline.tasks.TaskHandle.TaskProgressListener {
                override fun onStateChanged(state: com.litecut.app.timeline.tasks.TaskState) {
                    if (state == com.litecut.app.timeline.tasks.TaskState.COMPLETED) {
                        val result = handle.join()
                        if (result is com.litecut.app.timeline.tasks.TaskResult.Success) {
                            val data = result.value
                            cache.putWaveform(clip.id, data)
                            pendingTaskIds.remove(clip.id)
                            onComplete(data)
                        }
                    } else if (state == com.litecut.app.timeline.tasks.TaskState.FAILED || state == com.litecut.app.timeline.tasks.TaskState.CANCELLED) {
                        pendingTaskIds.remove(clip.id)
                    }
                }

                override fun onProgressUpdated(progress: Int) {}
            }
            handle.addListener(listener)
        }
    }

    /**
     * Updates viewport-aware priority, cancellation, and prefetching.
     * Pins currently visible waveforms to prevent eviction under memory pressure.
     * Modifies priorities of running tasks so that visible clips are TaskPriority.HIGH,
     * and offscreen/far clips are TaskPriority.LOW.
     * Cancels tasks for clips that are extremely far from the viewport to save resources.
     */
    fun updateViewportState(clips: List<Clip>, viewport: Viewport, pixelsPerSecond: Double) {
        val scheduler = TaskScheduler.getInstance(context)
        cache.clearPinned() // Start fresh pin set for this frame

        for (clip in clips) {
            if (clip.type != ClipType.AUDIO) continue

            val isVisible = viewport.isClipVisible(clip, pixelsPerSecond)
            
            // If visible, pin it in the cache to avoid LRU eviction under memory pressure
            if (isVisible) {
                cache.pinWaveform(clip.id)
            }

            // Check if there's a task pending for this clip
            val taskId = "waveform-${clip.id}"
            val activeTask = scheduler.getTask(taskId) as? WaveformWorker
            
            if (activeTask != null) {
                if (isVisible) {
                    // Elevate priority for visible clips
                    activeTask.priority = TaskPriority.HIGH
                } else {
                    // Check distance from viewport for offscreen clips
                    val leftPx = clip.leftSeconds * pixelsPerSecond
                    val rightPx = (clip.leftSeconds + clip.durationSeconds) * pixelsPerSecond
                    val scrollX = viewport.scrollX
                    val viewportWidth = viewport.width

                    val distancePx = when {
                        rightPx < scrollX -> scrollX - rightPx
                        leftPx > scrollX + viewportWidth -> leftPx - (scrollX + viewportWidth)
                        else -> 0.0
                    }

                    // Predictive pre-fetch window: within 2 viewport widths
                    val prefetchThreshold = viewportWidth * 2.0
                    if (distancePx <= prefetchThreshold) {
                        activeTask.priority = TaskPriority.NORMAL
                    } else if (distancePx > viewportWidth * 6.0) {
                        // Cancel extremely distant background generation tasks
                        Log.d(TAG, "Cancelling distant background task for clip: ${clip.id}")
                        scheduler.cancel(taskId)
                        pendingTaskIds.remove(clip.id)
                    } else {
                        // Background priority for offscreen but relatively close clips
                        activeTask.priority = TaskPriority.LOW
                    }
                }
            } else {
                // If it's not cached and not currently running, trigger prefetching if visible or near
                val isCached = cache.getWaveform(clip.id) != null
                if (!isCached && !pendingTaskIds.contains(clip.id)) {
                    val leftPx = clip.leftSeconds * pixelsPerSecond
                    val rightPx = (clip.leftSeconds + clip.durationSeconds) * pixelsPerSecond
                    val scrollX = viewport.scrollX
                    val viewportWidth = viewport.width

                    val distancePx = when {
                        rightPx < scrollX -> scrollX - rightPx
                        leftPx > scrollX + viewportWidth -> leftPx - (scrollX + viewportWidth)
                        else -> 0.0
                    }

                    val prefetchThreshold = viewportWidth * 2.0
                    if (isVisible || distancePx <= prefetchThreshold) {
                        val priority = if (isVisible) TaskPriority.HIGH else TaskPriority.NORMAL
                        requestWaveformAsynchronously(clip, priority) {
                            // Automatically triggers redraw on completed load
                        }
                    }
                }
            }
        }
    }

    fun clear() {
        cache.clear()
        pendingTaskIds.clear()
    }
}
