package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.tasks.TaskScheduler
import com.litecut.app.timeline.tasks.TaskHandle
import com.litecut.app.timeline.tasks.TaskPriority
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SeekController(
    private val context: Context,
    private val metrics: PlaybackMetrics,
    private val onSeekFrameReady: (Double) -> Unit
) {
    private val scheduler = TaskScheduler.getInstance(context)
    private val currentSeekHandle = AtomicReference<TaskHandle<*>?>(null)
    private val pendingSeekTime = AtomicReference<Double?>(null)
    private val isSeeking = AtomicBoolean(false)

    /**
     * Request an asynchronous, non-blocking seek to the specified timestamp.
     * If a previous seek is already running or queued, it is cancelled immediately
     * to avoid decoding overhead on outdated frames.
     */
    fun requestSeek(seconds: Double, isScrubbing: Boolean = false) {
        pendingSeekTime.set(seconds)

        // If currently seeking, cancel the active seek handle immediately
        val activeHandle = currentSeekHandle.getAndSet(null)
        if (activeHandle != null && !activeHandle.isDone) {
            activeHandle.cancel()
        }

        if (isSeeking.get() && isScrubbing) {
            // If already performing a seek during scrubbing, let the current one finish
            // and it will automatically pick up the newest pending target.
            return
        }

        triggerNextSeek()
    }

    @Synchronized
    private fun triggerNextSeek() {
        val target = pendingSeekTime.getAndSet(null) ?: return
        isSeeking.set(true)
        metrics.recordSeek()

        // Submit the asynchronous decode/seek task with HIGH priority
        val handle = scheduler.submit(
            name = "AsyncSeek-${target}",
            priority = TaskPriority.HIGH
        ) { token, _ ->
            // Simulate/perform the decode frame seek operation
            if (token.isCancelled()) return@submit

            // Perform keyframe evaluation and coordinate rendering
            // (The renderer will pick up this time and render the frame)
            
            if (!token.isCancelled()) {
                onSeekFrameReady(target)
            }
        }

        currentSeekHandle.set(handle)

        // Monitor completion and schedule next pending seek if any
        handle.addListener(object : TaskHandle.TaskProgressListener {
            override fun onStateChanged(state: com.litecut.app.timeline.tasks.TaskState) {
                if (state == com.litecut.app.timeline.tasks.TaskState.COMPLETED || 
                    state == com.litecut.app.timeline.tasks.TaskState.FAILED || 
                    state == com.litecut.app.timeline.tasks.TaskState.CANCELLED) {
                    
                    isSeeking.set(false)
                    // If a new seek request arrived while this one was running, process it now
                    if (pendingSeekTime.get() != null) {
                        triggerNextSeek()
                    }
                }
            }

            override fun onProgressUpdated(progress: Int) {}
        })
    }

    fun isSeeking(): Boolean = isSeeking.get()

    fun cancelAll() {
        val activeHandle = currentSeekHandle.getAndSet(null)
        activeHandle?.cancel()
        pendingSeekTime.set(null)
        isSeeking.set(false)
    }
}
