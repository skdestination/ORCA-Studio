package com.litecut.app.timeline

class AddMaskCommand(
    private val clipId: String,
    private val mask: Mask
) : Command {
    override fun execute(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        stack.masks.add(mask)
        maskEngine.notifyStackChanged(clipId)
    }

    override fun undo(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        stack.masks.removeAll { it.id == mask.id }
        maskEngine.notifyStackChanged(clipId)
    }
}

class DeleteMaskCommand(
    private val clipId: String,
    private val maskId: String
) : Command {
    private var removedMask: Mask? = null
    private var removedIndex: Int = -1

    override fun execute(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        
        val index = stack.masks.indexOfFirst { it.id == maskId }
        if (index != -1) {
            removedIndex = index
            removedMask = stack.masks.removeAt(index)
            maskEngine.notifyStackChanged(clipId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        val mask = removedMask
        val index = removedIndex
        if (mask != null && index != -1) {
            if (index <= stack.masks.size) {
                stack.masks.add(index, mask)
            } else {
                stack.masks.add(mask)
            }
            maskEngine.notifyStackChanged(clipId)
        }
    }
}

class MoveMaskCommand(
    private val clipId: String,
    private val fromIndex: Int,
    private val toIndex: Int
) : Command {
    override fun execute(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        if (fromIndex >= 0 && fromIndex < stack.masks.size && toIndex >= 0 && toIndex < stack.masks.size) {
            val mask = stack.masks.removeAt(fromIndex)
            stack.masks.add(toIndex, mask)
            maskEngine.notifyStackChanged(clipId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        if (toIndex >= 0 && toIndex < stack.masks.size && fromIndex >= 0 && fromIndex < stack.masks.size) {
            val mask = stack.masks.removeAt(toIndex)
            stack.masks.add(fromIndex, mask)
            maskEngine.notifyStackChanged(clipId)
        }
    }
}

class DuplicateMaskCommand(
    private val clipId: String,
    private val maskId: String,
    private val duplicatedMaskId: String
) : Command {
    private var duplicatedMask: Mask? = null

    override fun execute(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        val original = stack.masks.find { it.id == maskId }
        if (original != null) {
            val copy = original.copy().copy(id = duplicatedMaskId, name = "${original.name} Copy")
            duplicatedMask = copy
            stack.masks.add(copy)
            maskEngine.notifyStackChanged(clipId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        stack.masks.removeAll { it.id == duplicatedMaskId }
        maskEngine.notifyStackChanged(clipId)
    }
}

class ModifyMaskParameterCommand(
    private val clipId: String,
    private val maskId: String,
    private val action: (Mask) -> Unit,
    private val undoAction: (Mask) -> Unit
) : Command {
    override fun execute(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        val mask = stack.masks.find { it.id == maskId }
        if (mask != null) {
            action(mask)
            maskEngine.notifyStackChanged(clipId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val maskEngine = MaskEngine.getInstance(engine)
        val stack = maskEngine.getOrCreateStackForClip(clipId)
        val mask = stack.masks.find { it.id == maskId }
        if (mask != null) {
            undoAction(mask)
            maskEngine.notifyStackChanged(clipId)
        }
    }
}
