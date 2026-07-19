package com.litecut.app.timeline.commands

import com.litecut.app.timeline.Command
import com.litecut.app.timeline.TimelineEngine

class SplitClipCommand(
    private val clipId: String,
    private val splitTime: Double,
    private val generatedNewId: String
) : Command {
    private var oldDuration: Double = 0.0
    private var splitSuccessful = false

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        oldDuration = clip.durationSeconds

        val newClip = engine.splitClipInternal(clipId, splitTime, generatedNewId)
        if (newClip != null) {
            splitSuccessful = true
        }
    }

    override fun undo(engine: TimelineEngine) {
        if (splitSuccessful) {
            engine.deleteClipInternal(generatedNewId)
            engine.getClip(clipId)?.let {
                it.durationSeconds = oldDuration
            }
        }
    }
}
