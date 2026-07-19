package com.litecut.app.timeline.commands

import com.litecut.app.timeline.Command
import com.litecut.app.timeline.TimelineEngine
import com.litecut.app.timeline.TimelineClip

class DuplicateClipCommand(
    private val originalClipId: String,
    private val duplicatedClipId: String,
    private val newStartTime: Double
) : Command {
    private var duplicatedClip: TimelineClip? = null

    override fun execute(engine: TimelineEngine) {
        val original = engine.getClip(originalClipId) ?: return
        val dup = original.copy(
            id = duplicatedClipId,
            startTime = newStartTime
        )
        // copy effects and keyframes maps explicitly to prevent reference leaks
        dup.effects.clear()
        dup.effects.addAll(original.effects)
        
        dup.keyframes.clear()
        original.keyframes.forEach { (prop, list) ->
            dup.keyframes[prop] = list.toMutableList()
        }

        duplicatedClip = dup
        engine.addClipInternal(dup)
    }

    override fun undo(engine: TimelineEngine) {
        engine.deleteClipInternal(duplicatedClipId)
    }
}
