package com.litecut.app.timeline.tasks

import java.util.concurrent.CopyOnWriteArrayList

abstract class Task<T>(
    val id: String,
    val name: String,
    var priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy.NoRetry
) {
    val dependencies = CopyOnWriteArrayList<String>()
    val cancellationToken = CancellationToken()
    
    @Volatile
    var state: TaskState = TaskState.QUEUED
    
    @Volatile
    var progress: Int = 0

    @Volatile
    var attemptCount: Int = 0

    val createdAt: Long = System.currentTimeMillis()
    
    @Volatile
    var startedAt: Long = 0L
    
    @Volatile
    var completedAt: Long = 0L

    var result: TaskResult<T>? = null

    /**
     * Executes the heavy background logic.
     * Cooperates with the cancellationToken periodically.
     */
    @Throws(Exception::class)
    abstract fun execute(token: CancellationToken, onProgressUpdate: (Int) -> Unit): T

    /**
     * Helper to add a dependency task ID.
     */
    fun addDependency(taskId: String) {
        if (taskId != id && !dependencies.contains(taskId)) {
            dependencies.add(taskId)
        }
    }
}
