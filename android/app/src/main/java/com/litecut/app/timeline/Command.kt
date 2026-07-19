package com.litecut.app.timeline

import kotlin.math.max

interface Command {
    fun execute(engine: TimelineEngine)
    fun undo(engine: TimelineEngine)
}

class CreateClipCommand(private val clip: Clip) : Command {
    override fun execute(engine: TimelineEngine) {
        engine.addClipInternal(clip)
    }

    override fun undo(engine: TimelineEngine) {
        engine.deleteClipInternal(clip.id)
    }
}

class DeleteCommand(private val clipIds: List<String>) : Command {
    private val deletedClips = ArrayList<Clip>()

    override fun execute(engine: TimelineEngine) {
        deletedClips.clear()
        for (id in clipIds) {
            engine.getClip(id)?.let {
                deletedClips.add(it.copy(additionalProperties = it.additionalProperties.toMutableMap()))
            }
        }
        engine.deleteClipsInternal(clipIds)
    }

    override fun undo(engine: TimelineEngine) {
        for (clip in deletedClips) {
            engine.addClipInternal(clip)
        }
    }
}

class SplitCommand(
    private val clipId: String,
    private val splitTime: Double,
    private val generatedId: String
) : Command {
    private var oldDuration: Double = 0.0
    private var splitSuccessful = false

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        oldDuration = clip.durationSeconds

        val newClip = engine.splitClipInternal(clipId, splitTime, generatedId)
        if (newClip != null) {
            splitSuccessful = true
        }
    }

    override fun undo(engine: TimelineEngine) {
        if (splitSuccessful) {
            engine.deleteClipInternal(generatedId)
            engine.getClip(clipId)?.let {
                it.durationSeconds = oldDuration
            }
        }
    }
}

class MoveCommand(
    private val clipIds: List<String>,
    private val deltaSeconds: Double,
    private val targetLayerId: String,
    private val fallbackLayerId: String
) : Command {
    private val oldPositions = HashMap<String, Pair<Double, String>>()

    override fun execute(engine: TimelineEngine) {
        oldPositions.clear()
        for (id in clipIds) {
            engine.getClip(id)?.let {
                oldPositions[id] = Pair(it.leftSeconds, it.layerId)
            }
        }
        engine.moveClipsInternal(clipIds, deltaSeconds, targetLayerId, fallbackLayerId)
    }

    override fun undo(engine: TimelineEngine) {
        for ((id, pos) in oldPositions) {
            engine.getClip(id)?.let {
                it.leftSeconds = pos.first
                it.layerId = pos.second
            }
        }
    }
}

class TrimCommand(
    private val clipId: String,
    private val side: String,
    private val deltaSeconds: Double,
    private val snappingEnabled: Boolean,
    private val currentTime: Double
) : Command {
    private var oldLeft: Double = 0.0
    private var oldDuration: Double = 0.0
    private var oldTrimStart: Double = 0.0

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        oldLeft = clip.leftSeconds
        oldDuration = clip.durationSeconds
        oldTrimStart = clip.trimStartSeconds

        engine.trimClipInternal(clipId, side, deltaSeconds, snappingEnabled, currentTime)
    }

    override fun undo(engine: TimelineEngine) {
        engine.getClip(clipId)?.let {
            it.leftSeconds = oldLeft
            it.durationSeconds = oldDuration
            it.trimStartSeconds = oldTrimStart
        }
    }
}

class RippleDeleteCommand(private val clipId: String) : Command {
    private var deletedClip: Clip? = null
    private val shiftedClips = ArrayList<Pair<String, Double>>()

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        deletedClip = clip.copy(additionalProperties = clip.additionalProperties.toMutableMap())
        shiftedClips.clear()

        val deleteLeft = clip.leftSeconds
        val deleteDur = clip.durationSeconds
        val targetLayer = clip.layerId

        for (other in engine.getAllClips()) {
            if (other.layerId == targetLayer && other.leftSeconds > deleteLeft) {
                shiftedClips.add(Pair(other.id, other.leftSeconds))
            }
        }

        engine.rippleDeleteClipInternal(clipId)
    }

    override fun undo(engine: TimelineEngine) {
        deletedClip?.let { engine.addClipInternal(it) }
        for ((id, oldLeft) in shiftedClips) {
            engine.getClip(id)?.let {
                it.leftSeconds = oldLeft
            }
        }
    }
}
