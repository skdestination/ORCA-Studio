package com.litecut.app.timeline

class AddTextLayerCommand(
    private val clipId: String,
    private val textLayer: TextLayer
) : Command {
    override fun execute(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        textEngine.addTextLayerDirect(clipId, textLayer)
    }

    override fun undo(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        textEngine.removeTextLayerDirect(clipId, textLayer.id)
    }
}

class DeleteTextLayerCommand(
    private val clipId: String,
    private val layerId: String
) : Command {
    private var removedLayer: TextLayer? = null

    override fun execute(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        removedLayer = textEngine.getTextLayer(clipId, layerId)
        textEngine.removeTextLayerDirect(clipId, layerId)
    }

    override fun undo(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        val layer = removedLayer
        if (layer != null) {
            textEngine.addTextLayerDirect(clipId, layer)
        }
    }
}

class DuplicateTextLayerCommand(
    private val clipId: String,
    private val layerId: String,
    private val duplicatedLayerId: String
) : Command {
    private var duplicatedLayer: TextLayer? = null

    override fun execute(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        val original = textEngine.getTextLayer(clipId, layerId)
        if (original != null) {
            val copy = original.copy().copy(id = duplicatedLayerId, name = "${original.name} Copy")
            duplicatedLayer = copy
            textEngine.addTextLayerDirect(clipId, copy)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        textEngine.removeTextLayerDirect(clipId, duplicatedLayerId)
    }
}

class ModifyTextContentCommand(
    private val clipId: String,
    private val layerId: String,
    private val oldText: String,
    private val newText: String
) : Command {
    override fun execute(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        textEngine.updateTextContentDirect(clipId, layerId, newText)
    }

    override fun undo(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        textEngine.updateTextContentDirect(clipId, layerId, oldText)
    }
}

class ModifyTextStyleCommand(
    private val clipId: String,
    private val layerId: String,
    private val oldStyle: TextStyle,
    private val newStyle: TextStyle
) : Command {
    override fun execute(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        val layer = textEngine.getTextLayer(clipId, layerId)
        if (layer != null) {
            layer.document.rootStyle.copyFrom(newStyle)
            textEngine.notifyTextLayerChanged(clipId, layerId)
        }
    }

    override fun undo(engine: TimelineEngine) {
        val textEngine = TextEngine.getInstance(engine)
        val layer = textEngine.getTextLayer(clipId, layerId)
        if (layer != null) {
            layer.document.rootStyle.copyFrom(oldStyle)
            textEngine.notifyTextLayerChanged(clipId, layerId)
        }
    }
}
