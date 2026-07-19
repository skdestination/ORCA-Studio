package com.litecut.app.timeline.commands

import com.litecut.app.timeline.Command
import com.litecut.app.timeline.TimelineEngine

class MoveClipCommand(
    private val clipIds: List<String>,
    private val deltaSeconds: Double,
    private val targetLayerId: String,
    private val fallbackLayerId: String
) : Command {
    private var oldPositions: Map<String, Pair<Double, String>> = emptyMap()

    override fun execute(engine: TimelineEngine) {
        oldPositions = clipIds.mapNotNull { id ->
            engine.getClip(id)?.let { id to Pair(it.startTime, it.trackId) }
        }.toMap()

        engine.moveClipsInternal(clipIds, deltaSeconds, targetLayerId, fallbackLayerId)
    }

    override fun undo(engine: TimelineEngine) {
        for ((id, pos) in oldPositions) {
            engine.getClip(id)?.let {
                it.startTime = pos.first
                it.trackId = pos.second
            }
        }
    }
}
