package com.litecut.app.timeline

import java.util.Stack

class UndoManager(private val maxHistory: Int = 100) {
    private val undoStack = Stack<Command>()
    private val redoStack = Stack<Command>()

    fun executeCommand(command: Command, engine: TimelineEngine) {
        command.execute(engine)
        undoStack.push(command)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(engine: TimelineEngine) {
        if (undoStack.isNotEmpty()) {
            val cmd = undoStack.pop()
            cmd.undo(engine)
            redoStack.push(cmd)
        }
    }

    fun redo(engine: TimelineEngine) {
        if (redoStack.isNotEmpty()) {
            val cmd = redoStack.pop()
            cmd.execute(engine)
            undoStack.push(cmd)
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
