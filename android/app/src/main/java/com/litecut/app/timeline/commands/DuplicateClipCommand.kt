package com.litecut.app.timeline.commands

import com.litecut.app.timeline.Command
import com.litecut.app.timeline.TimelineEngine
import com.litecut.app.timeline.Clip

class DuplicateClipCommand(
    private val originalClipId: String,
    private val duplicatedClipId: String,
    private val newStartTime: Double
) : Command {
    private var duplicatedClip: Clip? = null

    override fun execute(engine: TimelineEngine) {
        val original = engine.getClip(originalClipId) ?: return
        val dup = original.copy(
            id = duplicatedClipId,
            leftSeconds = newStartTime,
            additionalProperties = original.additionalProperties.toMutableMap()
        )

        duplicatedClip = dup
        engine.addClipInternal(dup)
    }

    override fun undo(engine: TimelineEngine) {
        engine.deleteClipInternal(duplicatedClipId)
    }
}
