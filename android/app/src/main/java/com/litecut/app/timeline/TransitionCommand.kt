package com.litecut.app.timeline

class AddTransitionCommand(
    private val transition: Transition
) : Command {
    override fun execute(engine: TimelineEngine) {
        TransitionEngine.getInstance().addTransition(transition)
    }

    override fun undo(engine: TimelineEngine) {
        TransitionEngine.getInstance().removeTransition(transition.layerId, transition.id)
    }
}

class DeleteTransitionCommand(
    private val layerId: String,
    private val transitionId: String
) : Command {
    private var deletedTransition: Transition? = null

    override fun execute(engine: TimelineEngine) {
        deletedTransition = TransitionEngine.getInstance().removeTransition(layerId, transitionId)
    }

    override fun undo(engine: TimelineEngine) {
        deletedTransition?.let {
            TransitionEngine.getInstance().addTransition(it)
        }
    }
}

class MoveTransitionCommand(
    private val layerId: String,
    private val transitionId: String,
    private val newCenterTime: Double
) : Command {
    private var oldCenterTime: Double = 0.0

    override fun execute(engine: TimelineEngine) {
        val transition = TransitionEngine.getInstance().getTrack(layerId).getTransitions().find { it.id == transitionId }
        if (transition != null) {
            oldCenterTime = transition.centerTimeSeconds
            transition.centerTimeSeconds = newCenterTime
            TransitionEngine.getInstance().getTrack(layerId).sortTransitions()
        }
    }

    override fun undo(engine: TimelineEngine) {
        val transition = TransitionEngine.getInstance().getTrack(layerId).getTransitions().find { it.id == transitionId }
        if (transition != null) {
            transition.centerTimeSeconds = oldCenterTime
            TransitionEngine.getInstance().getTrack(layerId).sortTransitions()
        }
    }
}

class ChangeTransitionDurationCommand(
    private val layerId: String,
    private val transitionId: String,
    private val newDuration: Double
) : Command {
    private var oldDuration: Double = 1.0

    override fun execute(engine: TimelineEngine) {
        val transition = TransitionEngine.getInstance().getTrack(layerId).getTransitions().find { it.id == transitionId }
        if (transition != null) {
            oldDuration = transition.durationSeconds
            transition.durationSeconds = newDuration
        }
    }

    override fun undo(engine: TimelineEngine) {
        val transition = TransitionEngine.getInstance().getTrack(layerId).getTransitions().find { it.id == transitionId }
        if (transition != null) {
            transition.durationSeconds = oldDuration
        }
    }
}

class ChangeTransitionTypeCommand(
    private val layerId: String,
    private val transitionId: String,
    private val newType: TransitionType
) : Command {
    private var oldType: TransitionType = TransitionType.CROSS_DISSOLVE

    override fun execute(engine: TimelineEngine) {
        val transition = TransitionEngine.getInstance().getTrack(layerId).getTransitions().find { it.id == transitionId }
        if (transition != null) {
            oldType = transition.type
            transition.type = newType
        }
    }

    override fun undo(engine: TimelineEngine) {
        val transition = TransitionEngine.getInstance().getTrack(layerId).getTransitions().find { it.id == transitionId }
        if (transition != null) {
            transition.type = oldType
        }
    }
}
