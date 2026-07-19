package com.litecut.app.timeline

class AddKeyframeCommand(
    private val clipId: String,
    private val keyframe: Keyframe
) : Command {
    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        KeyframeEngine.addKeyframeInternal(clip, keyframe)
    }

    override fun undo(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        KeyframeEngine.removeKeyframeInternal(clip, keyframe.id)
    }
}

class DeleteKeyframeCommand(
    private val clipId: String,
    private val keyframeId: String
) : Command {
    private var deletedKeyframe: Keyframe? = null

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        deletedKeyframe = KeyframeEngine.findKeyframeById(clip, keyframeId)
        KeyframeEngine.removeKeyframeInternal(clip, keyframeId)
    }

    override fun undo(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        deletedKeyframe?.let {
            KeyframeEngine.addKeyframeInternal(clip, it)
        }
    }
}

class MoveKeyframeCommand(
    private val clipId: String,
    private val keyframeId: String,
    private val newTimeOffset: Double
) : Command {
    private var oldTimeOffset: Double = 0.0

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        val kf = KeyframeEngine.findKeyframeById(clip, keyframeId) ?: return
        oldTimeOffset = kf.timeOffset
        KeyframeEngine.updateKeyframeInternal(clip, keyframeId, newTimeOffset = newTimeOffset)
    }

    override fun undo(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        KeyframeEngine.updateKeyframeInternal(clip, keyframeId, newTimeOffset = oldTimeOffset)
    }
}

class ChangeKeyframeValueCommand(
    private val clipId: String,
    private val keyframeId: String,
    private val newValue: Double
) : Command {
    private var oldValue: Double = 0.0

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        val kf = KeyframeEngine.findKeyframeById(clip, keyframeId) ?: return
        oldValue = kf.value
        KeyframeEngine.updateKeyframeInternal(clip, keyframeId, newValue = newValue)
    }

    override fun undo(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        KeyframeEngine.updateKeyframeInternal(clip, keyframeId, newValue = oldValue)
    }
}

class ChangeKeyframeInterpolationCommand(
    private val clipId: String,
    private val keyframeId: String,
    private val newInterpolation: InterpolationType
) : Command {
    private var oldInterpolation: InterpolationType = InterpolationType.LINEAR

    override fun execute(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        val kf = KeyframeEngine.findKeyframeById(clip, keyframeId) ?: return
        oldInterpolation = kf.interpolation
        KeyframeEngine.updateKeyframeInternal(clip, keyframeId, newInterpolation = newInterpolation)
    }

    override fun undo(engine: TimelineEngine) {
        val clip = engine.getClip(clipId) ?: return
        KeyframeEngine.updateKeyframeInternal(clip, keyframeId, newInterpolation = oldInterpolation)
    }
}
