package com.litecut.app.timeline

import java.util.concurrent.CopyOnWriteArrayList

class TransitionTrack(val layerId: String) {
    private val transitions = CopyOnWriteArrayList<Transition>()

    fun addTransition(transition: Transition) {
        // Ensure no duplicate transition at the same centerTimeSeconds
        transitions.removeAll { it.id == transition.id || it.centerTimeSeconds == transition.centerTimeSeconds }
        transitions.add(transition)
        sortTransitions()
    }

    fun removeTransition(transitionId: String): Transition? {
        val found = transitions.find { it.id == transitionId }
        if (found != null) {
            transitions.remove(found)
        }
        return found
    }

    fun getTransitions(): List<Transition> {
        return transitions.toList()
    }

    fun getTransitionAtTime(timeSeconds: Double): Transition? {
        return transitions.find { t ->
            val halfDur = t.durationSeconds / 2.0
            timeSeconds >= (t.centerTimeSeconds - halfDur) && timeSeconds <= (t.centerTimeSeconds + halfDur)
        }
    }

    fun clear() {
        transitions.clear()
    }

    fun sortTransitions() {
        val sortedList = transitions.sortedBy { it.centerTimeSeconds }
        transitions.clear()
        transitions.addAll(sortedList)
    }
}
