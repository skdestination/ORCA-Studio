package com.litecut.app.timeline

interface Command {
    fun execute(engine: TimelineEngine)
    fun undo(engine: TimelineEngine)
}

class MoveCommand(
    private val clipIds: List<String>,
    private val deltaSeconds: Double,
    private val targetLayerId: String,
    private val fallbackLayerId: String
) : Command {
    private var oldState: Map<String, Pair<Double, String>> = emptyMap()
    private var executed = false

    override fun execute(engine: TimelineEngine) {
        // Record old positions and layers
        oldState = clipIds.mapNotNull { id ->
            engine.getClip(id)?.let { id to Pair(it.leftSeconds, it.layerId) }
        }.toMap()

        engine.moveClipsInternal(clipIds, deltaSeconds, targetLayerId, fallbackLayerId)
        executed = true
    }

    override fun undo(engine: TimelineEngine) {
        if (!executed) return
        for ((id, pos) in oldState) {
            engine.getClip(id)?.let {
                it.leftSeconds = pos.first
                it.layerId = pos.second
            }
        }
        executed = false
    }
}

class TrimCommand(
    private val clipId: String,
    private val side: String, // "left" or "right"
    private val deltaSeconds: Double,
    private val snappingEnabled: Boolean,
    private val currentTime: Double
) : Command {
    private var oldLeft: Double = 0.0
    private var oldDuration: Double = 0.0
    private var oldTrimStart: Double = 0.0
    private var executed = false

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        oldLeft = clip.leftSeconds
        oldDuration = clip.durationSeconds
        oldTrimStart = clip.trimStartSeconds

        engine.trimClipInternal(clipId, side, deltaSeconds, snappingEnabled, currentTime)
        executed = true
    }

    override fun undo(engine: TimelineEngine) {
        if (!executed) return
        engine.getClip(clipId)?.let {
            it.leftSeconds = oldLeft
            it.durationSeconds = oldDuration
            it.trimStartSeconds = oldTrimStart
        }
        executed = false
    }
}

class SplitCommand(
    private val clipId: String,
    private val splitTime: Double,
    private val generatedNewId: String
) : Command {
    private var splitIndex: Int = -1
    private var addedClip: Clip? = null
    private var oldDuration: Double = 0.0
    private var executed = false

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        oldDuration = clip.durationSeconds

        val newClip = engine.splitClipInternal(clipId, splitTime, generatedNewId)
        if (newClip != null) {
            addedClip = newClip
            executed = true
        }
    }

    override fun undo(engine: TimelineEngine) {
        if (!executed) return
        val clip = engine.getClip(clipId) ?: return
        clip.durationSeconds = oldDuration
        addedClip?.let {
            engine.deleteClipInternal(it.id)
        }
        executed = false
    }
}

class DeleteCommand(
    private val clipIds: List<String>
) : Command {
    private var deletedClips: List<Clip> = emptyList()
    private var executed = false

    override fun execute(engine: TimelineEngine) {
        deletedClips = clipIds.mapNotNull { engine.getClip(it)?.copy() }
        engine.deleteClipsInternal(clipIds)
        executed = true
    }

    override fun undo(engine: TimelineEngine) {
        if (!executed) return
        deletedClips.forEach {
            engine.addClipInternal(it)
        }
        executed = false
    }
}

class RippleDeleteCommand(
    private val clipId: String
) : Command {
    private var oldClipsState: List<Pair<String, Double>> = emptyList()
    private var deletedClip: Clip? = null
    private var executed = false

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        deletedClip = clip.copy()
        
        // Record all other clip positions
        oldClipsState = engine.getAllClips().map { it.id to it.leftSeconds }

        engine.rippleDeleteClipInternal(clipId)
        executed = true
    }

    override fun undo(engine: TimelineEngine) {
        if (!executed) return
        // Restore all clips positions
        for ((id, left) in oldClipsState) {
            engine.getClip(id)?.leftSeconds = left
        }
        // Restore the deleted clip
        deletedClip?.let { engine.addClipInternal(it) }
        executed = false
    }
}

class CreateClipCommand(
    private val clip: Clip
) : Command {
    private var executed = false

    override fun execute(engine: TimelineEngine) {
        engine.addClipInternal(clip)
        executed = true
    }

    override fun undo(engine: TimelineEngine) {
        if (!executed) return
        engine.deleteClipInternal(clip.id)
        executed = false
    }
}
