package com.litecut.app.timeline

class AddEffectCommand(
    private val clipId: String,
    private val effect: Effect
) : Command {
    override fun execute(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        stack.effects.add(effect)
        effectsEngine.notifyStackChanged(clipId)
    }

    override fun undo(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        stack.effects.removeAll { it.id == effect.id }
        effectsEngine.notifyStackChanged(clipId)
    }
}

class DeleteEffectCommand(
    private val clipId: String,
    private val effectId: String
) : Command {
    private var removedEffect: Effect? = null
    private var removedIndex: Int = -1

    override fun execute(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        
        val index = stack.effects.indexOfFirst { it.id == effectId }
        if (index != -1) {
            removedIndex = index
            removedEffect = stack.effects.removeAt(index)
            effectsEngine.notifyStackChanged(clipId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        val effect = removedEffect
        val index = removedIndex
        if (effect != null && index != -1) {
            if (index <= stack.effects.size) {
                stack.effects.add(index, effect)
            } else {
                stack.effects.add(effect)
            }
            effectsEngine.notifyStackChanged(clipId)
        }
    }
}

class MoveEffectCommand(
    private val clipId: String,
    private val fromIndex: Int,
    private val toIndex: Int
) : Command {
    override fun execute(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        if (fromIndex >= 0 && fromIndex < stack.effects.size && toIndex >= 0 && toIndex < stack.effects.size) {
            val effect = stack.effects.removeAt(fromIndex)
            stack.effects.add(toIndex, effect)
            effectsEngine.notifyStackChanged(clipId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        // Reverse the move operation
        if (toIndex >= 0 && toIndex < stack.effects.size && fromIndex >= 0 && fromIndex < stack.effects.size) {
            val effect = stack.effects.removeAt(toIndex)
            stack.effects.add(fromIndex, effect)
            effectsEngine.notifyStackChanged(clipId)
        }
    }
}

class DuplicateEffectCommand(
    private val clipId: String,
    private val effectId: String,
    private val duplicatedEffectId: String
) : Command {
    private var duplicatedEffect: Effect? = null

    override fun execute(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        val original = stack.effects.find { it.id == effectId }
        if (original != null) {
            val copy = original.copy().copy(id = duplicatedEffectId, name = "${original.name} Copy")
            duplicatedEffect = copy
            stack.effects.add(copy)
            effectsEngine.notifyStackChanged(clipId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        stack.effects.removeAll { it.id == duplicatedEffectId }
        effectsEngine.notifyStackChanged(clipId)
    }
}

class ModifyEffectParameterCommand(
    private val clipId: String,
    private val effectId: String,
    private val parameterName: String,
    private val oldValue: Float,
    private val newValue: Float
) : Command {
    override fun execute(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        val effect = stack.effects.find { it.id == effectId }
        val parameter = effect?.parameters?.get(parameterName)
        if (parameter != null) {
            parameter.value = newValue
            effectsEngine.notifyStackChanged(clipId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val effectsEngine = EffectsEngine.getInstance(engine)
        val stack = effectsEngine.getOrCreateStackForClip(clipId)
        val effect = stack.effects.find { it.id == effectId }
        val parameter = effect?.parameters?.get(parameterName)
        if (parameter != null) {
            parameter.value = oldValue
            effectsEngine.notifyStackChanged(clipId)
        }
    }
}
