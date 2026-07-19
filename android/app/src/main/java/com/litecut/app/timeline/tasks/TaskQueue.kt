package com.litecut.app.timeline.tasks

import java.util.concurrent.ConcurrentHashMap

class TaskQueue {
    private val tasks = ArrayList<Task<*>>()
    private val completedTaskIds = ConcurrentHashMap.newKeySet<String>()
    private val runningTaskIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * Add a task to the queue.
     */
    @Synchronized
    fun enqueue(task: Task<*>) {
        // Prevent duplicate task submission
        if (tasks.any { it.id == task.id }) return
        
        tasks.add(task)
        completedTaskIds.remove(task.id)
    }

    /**
     * Find and poll the highest priority task that has all dependencies completed.
     */
    @Synchronized
    fun pollEligible(): Task<*>? {
        if (tasks.isEmpty()) return null

        // Sort by priority descending, then by creation time ascending
        val sorted = tasks.sortedWith(
            compareByDescending<Task<*>> { it.priority.value }
                .thenBy { it.createdAt }
        )

        for (task in sorted) {
            // Check if all dependencies are completed
            val dependenciesMet = task.dependencies.all { depId ->
                completedTaskIds.contains(depId)
            }

            if (dependenciesMet) {
                tasks.remove(task)
                runningTaskIds.add(task.id)
                return task
            }
        }
        return null
    }

    /**
     * Mark a task as completed.
     */
    fun markCompleted(taskId: String) {
        runningTaskIds.remove(taskId)
        completedTaskIds.add(taskId)
    }

    /**
     * Mark a task as failed / no longer running.
     */
    fun markFailed(taskId: String) {
        runningTaskIds.remove(taskId)
    }

    /**
     * Cancel a task and remove it from the queue.
     */
    @Synchronized
    fun cancel(taskId: String): Task<*>? {
        val iterator = tasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (task.id == taskId) {
                task.cancellationToken.cancel()
                task.state = TaskState.CANCELLED
                iterator.remove()
                return task
            }
        }
        runningTaskIds.remove(taskId)
        return null
    }

    @Synchronized
    fun updatePriority(taskId: String, newPriority: TaskPriority): Boolean {
        val task = tasks.find { it.id == taskId }
        if (task != null) {
            task.priority = newPriority
            return true
        }
        return false
    }

    @Synchronized
    fun getTasksSnapshot(): List<Task<*>> {
        return ArrayList(tasks)
    }

    @Synchronized
    fun clear() {
        tasks.clear()
        completedTaskIds.clear()
        runningTaskIds.clear()
    }

    @Synchronized
    fun size(): Int = tasks.size

    fun isRunning(taskId: String): Boolean = runningTaskIds.contains(taskId)
    fun isCompleted(taskId: String): Boolean = completedTaskIds.contains(taskId)
}
