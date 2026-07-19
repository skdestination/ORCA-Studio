package com.litecut.app.timeline

class ModifyColorAdjustmentCommand(
    private val clipId: String,
    private val isPreAdjustment: Boolean,
    private val oldAdjustment: ColorAdjustment,
    private val newAdjustment: ColorAdjustment
) : Command {
    override fun execute(engine: TimelineEngine) {
        val colorEngine = ColorEngine.getInstance(engine)
        val stack = colorEngine.getOrCreateStackForClip(clipId)
        if (isPreAdjustment) {
            stack.preAdjustments.copyFrom(newAdjustment)
        } else {
            stack.postAdjustments.copyFrom(newAdjustment)
        }
        colorEngine.notifyStackChanged(clipId)
    }

    override fun undo(engine: TimelineEngine) {
        val colorEngine = ColorEngine.getInstance(engine)
        val stack = colorEngine.getOrCreateStackForClip(clipId)
        if (isPreAdjustment) {
            stack.preAdjustments.copyFrom(oldAdjustment)
        } else {
            stack.postAdjustments.copyFrom(oldAdjustment)
        }
        colorEngine.notifyStackChanged(clipId)
    }
}

class ChangeLUTCommand(
    private val clipId: String,
    private val isPrimaryLut: Boolean,
    private val lutIndex: Int, // If index is -1, it appends or replaces, if it's existing it modifies
    private val oldLut: LUT?,
    private val newLut: LUT?
) : Command {
    override fun execute(engine: TimelineEngine) {
        val colorEngine = ColorEngine.getInstance(engine)
        val stack = colorEngine.getOrCreateStackForClip(clipId)
        val luts = if (isPrimaryLut) stack.primaryLuts else stack.secondaryLuts

        if (newLut == null) {
            // Remove LUT
            if (lutIndex >= 0 && lutIndex < luts.size) {
                luts.removeAt(lutIndex)
            }
        } else {
            // Add or Modify LUT
            if (lutIndex >= 0 && lutIndex < luts.size) {
                luts[lutIndex] = newLut
            } else {
                luts.add(newLut)
            }
        }
        colorEngine.notifyStackChanged(clipId)
    }

    override fun undo(engine: TimelineEngine) {
        val colorEngine = ColorEngine.getInstance(engine)
        val stack = colorEngine.getOrCreateStackForClip(clipId)
        val luts = if (isPrimaryLut) stack.primaryLuts else stack.secondaryLuts

        if (oldLut == null) {
            // Undo an add (which means remove the newly added LUT)
            val indexToRemove = if (lutIndex >= 0 && lutIndex < luts.size) lutIndex else luts.size - 1
            if (indexToRemove >= 0 && indexToRemove < luts.size) {
                luts.removeAt(indexToRemove)
            }
        } else {
            // Undo a modify or delete
            if (lutIndex >= 0 && lutIndex < luts.size) {
                luts[lutIndex] = oldLut
            } else {
                luts.add(oldLut)
            }
        }
        colorEngine.notifyStackChanged(clipId)
    }
}
