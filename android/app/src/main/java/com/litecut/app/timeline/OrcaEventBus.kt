package com.litecut.app.timeline

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * Immutable system events processed through the lightweight Orca event bus.
 */
sealed class OrcaEvent {
    // Clip management events
    data class ClipAdded(val clipId: String, val layerId: String, val startSeconds: Double, val durationSeconds: Double) : OrcaEvent()
    data class ClipRemoved(val clipId: String) : OrcaEvent()
    data class ClipModified(val clipId: String, val changeType: ClipChangeType) : OrcaEvent()
    
    // Track management events
    data class TrackChanged(val trackId: String) : OrcaEvent()
    
    // Playhead & Playback events
    data class PlayheadMoved(val positionSeconds: Double, val isScrubbing: Boolean) : OrcaEvent()
    data class PlaybackStarted(val positionSeconds: Double) : OrcaEvent()
    data class PlaybackPaused(val positionSeconds: Double) : OrcaEvent()
    
    // Scroll & UI navigation events
    data class TimelineScrolled(val scrollX: Int, val scrollY: Int) : OrcaEvent()
    data class TimelineZoomChanged(val zoomLevel: Float) : OrcaEvent()
    
    // Project lifecycle events
    data class ProjectLoaded(val projectId: String) : OrcaEvent()
    data class ProjectSaved(val projectId: String) : OrcaEvent()
    
    // Export lifecycle events
    data class ExportStarted(val jobId: String) : OrcaEvent()
    data class ExportFinished(val jobId: String, val success: Boolean, val path: String?) : OrcaEvent()
    
    // System health and cache management
    object MemoryPressure : OrcaEvent()
}

/**
 * Event listener interface for direct custom handling.
 */
fun interface OrcaEventListener {
    fun onEvent(event: OrcaEvent)
}

/**
 * OrcaEventBus operates as a thread-safe broker for events.
 * Provides fine-grained type-based subscription to completely decouple engines.
 */
class OrcaEventBus private constructor() {

    private val globalListeners = CopyOnWriteArrayList<OrcaEventListener>()
    private val typedListeners = ConcurrentHashMap<Class<out OrcaEvent>, CopyOnWriteArrayList<OrcaEventListener>>()

    companion object {
        @Volatile
        private var instance: OrcaEventBus? = null

        fun getInstance(): OrcaEventBus {
            return instance ?: synchronized(this) {
                instance ?: OrcaEventBus().also { instance = it }
            }
        }
    }

    /**
     * Subscribes a listener to ALL dispatched events on the bus.
     */
    fun subscribe(listener: OrcaEventListener) {
        if (!globalListeners.contains(listener)) {
            globalListeners.add(listener)
        }
    }

    /**
     * Unsubscribes a global listener.
     */
    fun unsubscribe(listener: OrcaEventListener) {
        globalListeners.remove(listener)
    }

    /**
     * Subscribes a listener to a specific subclass of OrcaEvent.
     */
    fun <T : OrcaEvent> subscribeTo(eventType: Class<T>, listener: OrcaEventListener) {
        val list = typedListeners.getOrPut(eventType) { CopyOnWriteArrayList() }
        if (!list.contains(listener)) {
            list.add(listener)
        }
    }

    /**
     * Unsubscribes a typed listener.
     */
    fun <T : OrcaEvent> unsubscribeFrom(eventType: Class<T>, listener: OrcaEventListener) {
        typedListeners[eventType]?.remove(listener)
    }

    /**
     * Dispatches an event synchronously and efficiently across registered listeners.
     * Operates with zero heap allocations for the dispatch loop itself.
     */
    fun publish(event: OrcaEvent) {
        // Dispatch to global subscribers
        val size = globalListeners.size
        for (i in 0 until size) {
            globalListeners[i].onEvent(event)
        }

        // Dispatch to specific typed subscribers
        val eventClass = event::class.java
        typedListeners[eventClass]?.let { list ->
            val listSize = list.size
            for (i in 0 until listSize) {
                list[i].onEvent(event)
            }
        }
    }

    /**
     * Completely purges all listeners to free references.
     */
    fun clear() {
        globalListeners.clear()
        typedListeners.clear()
        Log.d("OrcaEventBus", "Event bus registrations cleared successfully.")
    }
}
