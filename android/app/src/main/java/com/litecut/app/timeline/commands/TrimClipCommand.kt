package com.litecut.app.timeline.commands

import com.litecut.app.timeline.Command
import com.litecut.app.timeline.TimelineEngine

class TrimClipCommand(
    private val clipId: String,
    private val side: String, // "left" or "right"
    private val deltaSeconds: Double,
    private val snappingEnabled: Boolean,
    private val currentTime: Double
) : Command {
    private var oldLeft: Double = 0.0
    private var oldDuration: Double = 0.0
    private var oldTrimStart: Double = 0.0

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        oldLeft = clip.startTime
        oldDuration = clip.duration
        oldTrimStart = clip.trimIn

        engine.trimClipInternal(clipId, side, deltaSeconds, snappingEnabled, currentTime)
    }

    override fun undo(engine: TimelineEngine) {
        engine.getClip(clipId)?.let {
            it.startTime = oldLeft
            it.duration = oldDuration
            it.trimIn = oldTrimStart
        }
    }
}
