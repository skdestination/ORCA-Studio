package com.litecut.app.timeline.commands

import com.litecut.app.timeline.Command
import com.litecut.app.timeline.TimelineEngine
import com.litecut.app.timeline.TimelineClip

class DeleteClipCommand(private val clipIds: List<String>) : Command {
    private var deletedClips: List<TimelineClip> = emptyList()

    override fun execute(engine: TimelineEngine) {
        deletedClips = clipIds.mapNotNull { engine.getClip(it)?.copy() }
        engine.deleteClipsInternal(clipIds)
    }

    override fun undo(engine: TimelineEngine) {
        deletedClips.forEach {
            engine.addClipInternal(it)
        }
    }
}
