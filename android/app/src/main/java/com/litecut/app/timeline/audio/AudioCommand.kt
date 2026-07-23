package com.litecut.app.timeline.audio

import com.litecut.app.timeline.Command
import com.litecut.app.timeline.TimelineEngine

/**
 * Command to undo/redo Volume changes on a mixer channel or clip.
 */
class AdjustVolumeCommand(
    private val clipId: String,
    private val oldVolume: Float,
    private val newVolume: Float
) : Command {
    override fun execute(engine: TimelineEngine) {
        val channel = AudioMixerEngine.getInstance().getTrackForClip(clipId)?.getChannel(clipId)
        channel?.volume = newVolume
        
        // Also update standard fallback properties on the native clip if present
        engine.getClip(clipId)?.let { clip ->
            clip.additionalProperties["volume"] = newVolume.toDouble()
        }
    }

    override fun undo(engine: TimelineEngine) {
        val channel = AudioMixerEngine.getInstance().getTrackForClip(clipId)?.getChannel(clipId)
        channel?.volume = oldVolume
        
        engine.getClip(clipId)?.let { clip ->
            clip.additionalProperties["volume"] = oldVolume.toDouble()
        }
    }
}

/**
 * Command to undo/redo Pan changes.
 */
class AdjustPanCommand(
    private val clipId: String,
    private val oldPan: Float,
    private val newPan: Float
) : Command {
    override fun execute(engine: TimelineEngine) {
        val channel = AudioMixerEngine.getInstance().getTrackForClip(clipId)?.getChannel(clipId)
        channel?.pan = newPan
        
        engine.getClip(clipId)?.let { clip ->
            clip.additionalProperties["pan"] = newPan.toDouble()
        }
    }

    override fun undo(engine: TimelineEngine) {
        val channel = AudioMixerEngine.getInstance().getTrackForClip(clipId)?.getChannel(clipId)
        channel?.pan = oldPan
        
        engine.getClip(clipId)?.let { clip ->
            clip.additionalProperties["pan"] = oldPan.toDouble()
        }
    }
}

/**
 * Command to undo/redo Channel-to-Bus Routing changes.
 */
class ChangeBusRoutingCommand(
    private val clipId: String,
    private val oldBusId: String,
    private val newBusId: String
) : Command {
    override fun execute(engine: TimelineEngine) {
        val channel = AudioMixerEngine.getInstance().getTrackForClip(clipId)?.getChannel(clipId)
        channel?.targetBusId = newBusId
        
        engine.getClip(clipId)?.let { clip ->
            clip.additionalProperties["targetBusId"] = newBusId
        }
    }

    override fun undo(engine: TimelineEngine) {
        val channel = AudioMixerEngine.getInstance().getTrackForClip(clipId)?.getChannel(clipId)
        channel?.targetBusId = oldBusId
        
        engine.getClip(clipId)?.let { clip ->
            clip.additionalProperties["targetBusId"] = oldBusId
        }
    }
}
