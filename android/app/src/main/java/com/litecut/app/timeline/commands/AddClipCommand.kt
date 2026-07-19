package com.litecut.app.timeline.commands

import com.litecut.app.timeline.Command
import com.litecut.app.timeline.TimelineEngine
import com.litecut.app.timeline.TimelineClip

class AddClipCommand(private val clip: TimelineClip) : Command {
    override fun execute(engine: TimelineEngine) {
        engine.addClipInternal(clip)
    }

    override fun undo(engine: TimelineEngine) {
        engine.deleteClipInternal(clip.id)
    }
}
