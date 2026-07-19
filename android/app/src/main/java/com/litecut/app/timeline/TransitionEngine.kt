package com.litecut.app.timeline

import android.content.Context
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class TransitionEngine private constructor() {
    private val tracks = ConcurrentHashMap<String, TransitionTrack>()

    companion object {
        @Volatile
        private var instance: TransitionEngine? = null

        fun getInstance(): TransitionEngine {
            return instance ?: synchronized(this) {
                instance ?: TransitionEngine().also { instance = it }
            }
        }
    }

    fun getTrack(layerId: String): TransitionTrack {
        return tracks.getOrPut(layerId) { TransitionTrack(layerId) }
    }

    @Synchronized
    fun addTransition(transition: Transition) {
        val track = getTrack(transition.layerId)
        track.addTransition(transition)
    }

    @Synchronized
    fun removeTransition(layerId: String, transitionId: String): Transition? {
        val track = tracks[layerId] ?: return null
        return track.removeTransition(transitionId)
    }

    @Synchronized
    fun getTransitionAtTime(layerId: String, timeSeconds: Double): Transition? {
        val track = tracks[layerId] ?: return null
        return track.getTransitionAtTime(timeSeconds)
    }

    @Synchronized
    fun getAllTransitions(): List<Transition> {
        val all = ArrayList<Transition>()
        for (track in tracks.values) {
            all.addAll(track.getTransitions())
        }
        return all
    }

    /**
     * Scans the timeline and automatically detects boundary touch-points between adjacent clips
     * to insert or propose transitions. This is a high-end Adobe Premiere/CapCut desktop feature.
     */
    @Synchronized
    fun autoDetectAndApplyTransitions(timelineEngine: TimelineEngine, defaultType: TransitionType = TransitionType.CROSS_DISSOLVE, duration: Double = 1.0) {
        val clips = timelineEngine.getAllClips()
        val clipsByLayer = clips.groupBy { it.layerId }

        for ((layerId, layerClips) in clipsByLayer) {
            val sortedClips = layerClips.sortedBy { it.leftSeconds }
            for (i in 0 until sortedClips.size - 1) {
                val clipA = sortedClips[i]
                val clipB = sortedClips[i + 1]
                val endA = clipA.leftSeconds + clipA.durationSeconds
                val startB = clipB.leftSeconds

                // Touch or near touch (less than 100ms gap)
                if (abs(endA - startB) < 0.1) {
                    val centerTime = (endA + startB) / 2.0
                    val transitionId = "AutoTransition-${clipA.id}-${clipB.id}"
                    
                    val transition = Transition(
                        id = transitionId,
                        type = defaultType,
                        durationSeconds = duration,
                        centerTimeSeconds = centerTime,
                        outgoingClipId = clipA.id,
                        incomingClipId = clipB.id,
                        layerId = layerId
                    )
                    addTransition(transition)
                }
            }
        }
    }

    @Synchronized
    fun clear() {
        for (track in tracks.values) {
            track.clear()
        }
        tracks.clear()
    }
}
