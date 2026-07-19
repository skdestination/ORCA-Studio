package com.litecut.app.timeline.tasks

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap

class TaskDispatcher(
    private val context: Context,
    private val queue: TaskQueue,
    private val workerPool: WorkerPool
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeHandles = ConcurrentHashMap<String, TaskHandle<*>>()

    /**
     * Dispatcher lock to ensure only one thread triggers dispatch loops at once.
     */
    private val dispatchLock = Any()

    fun <T> submit(task: Task<T>): TaskHandle<T> {
        val handle = TaskHandle(task.id, task)
        activeHandles[task.id] = handle
        
        queue.enqueue(task)
        triggerDispatch()
        
        return handle
    }

    fun triggerDispatch() {
        workerPool.execute {
            synchronized(dispatchLock) {
                runDispatchLoop()
            }
        }
    }

    private fun runDispatchLoop() {
        while (true) {
            val task = queue.pollEligible() ?: break
            
            // Cooperate with ResourceManager: Pause or delay low-priority tasks if under heavy memory constraint
            if (shouldPostponeTask(task)) {
                Log.w("TaskDispatcher", "Postponing execution of task '${task.name}' due to high memory pressure.")
                // Re-enqueue task and break to let memory pressure subside
                queue.enqueue(task)
                queue.markFailed(task.id) // Unmark running
                break
            }

            // Execute task on worker pool
            dispatchTaskToWorker(task)
        }
    }

    private fun shouldPostponeTask(task: Task<*>): Boolean {
        // High priority or critical tasks are never postponed
        if (task.priority == TaskPriority.CRITICAL || task.priority == TaskPriority.HIGH) {
            return false
        }

        try {
            val resourceManager = ResourceManager.getInstance(context)
            val currentUsage = resourceManager.stats.currentMemoryUsageBytes.get()
            val maxBudget = resourceManager.budget.totalMaxMemoryBytes
            
            if (maxBudget > 0) {
                val ratio = currentUsage.toDouble() / maxBudget.toDouble()
                if (ratio > 0.90) {
                    // Over 90% memory category budget consumed - postpone low priority work!
                    return true
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> dispatchTaskToWorker(task: Task<T>) {
        val handle = activeHandles[task.id] as? TaskHandle<T> ?: return

        task.state = TaskState.RUNNING
        task.startedAt = System.currentTimeMillis()
        notifyStateChange(handle, TaskState.RUNNING)

        workerPool.execute {
            try {
                if (task.cancellationToken.isCancelled()) {
                    handleCancellation(task, handle)
                    return@execute
                }

                task.attemptCount++
                
                // Execute actual heavy task
                val resultValue = task.execute(task.cancellationToken) { progress ->
                    task.progress = progress
                    mainHandler.post {
                        handle.notifyProgressUpdated(progress)
                    }
                }

                // Check again for cancellation
                if (task.cancellationToken.isCancelled()) {
                    handleCancellation(task, handle)
                    return@execute
                }

                // Success path
                task.state = TaskState.COMPLETED
                task.completedAt = System.currentTimeMillis()
                queue.markCompleted(task.id)
                handle.complete(TaskResult.Success(resultValue))
                notifyStateChange(handle, TaskState.COMPLETED)

                // Clean active handle mapping to release references
                activeHandles.remove(task.id)

                // Dispatch any newly eligible dependent tasks!
                triggerDispatch()

            } catch (throwable: Throwable) {
                handleFailure(task, handle, throwable)
            }
        }
    }

    private fun <T> handleCancellation(task: Task<T>, handle: TaskHandle<T>) {
        task.state = TaskState.CANCELLED
        task.completedAt = System.currentTimeMillis()
        queue.markFailed(task.id)
        handle.complete(TaskResult.Failure(InterruptedException("Task cancelled cooperatively")))
        notifyStateChange(handle, TaskState.CANCELLED)
        activeHandles.remove(task.id)
        triggerDispatch()
    }

    private fun <T> handleFailure(task: Task<T>, handle: TaskHandle<T>, throwable: Throwable) {
        if (task.cancellationToken.isCancelled()) {
            handleCancellation(task, handle)
            return
        }

        val policy = task.retryPolicy
        if (policy.shouldRetry(task.attemptCount)) {
            task.state = TaskState.RETRYING
            notifyStateChange(handle, TaskState.RETRYING)

            val delayMs = policy.getBackoffMs(task.attemptCount)
            Log.w("TaskDispatcher", "Task '${task.name}' failed. Retrying (Attempt #${task.attemptCount}) in ${delayMs}ms...", throwable)

            workerPool.schedule({
                queue.markFailed(task.id) // Clear running mark
                queue.enqueue(task)       // Re-enqueue
                triggerDispatch()
            }, delayMs)
        } else {
            // Terminal Failure
            task.state = TaskState.FAILED
            task.completedAt = System.currentTimeMillis()
            queue.markFailed(task.id)
            handle.complete(TaskResult.Failure(throwable))
            notifyStateChange(handle, TaskState.FAILED)
            activeHandles.remove(task.id)
            
            Log.e("TaskDispatcher", "Task '${task.name}' failed terminally after ${task.attemptCount} attempts.", throwable)
            triggerDispatch()
        }
    }

    private fun <T> notifyStateChange(handle: TaskHandle<T>, state: TaskState) {
        mainHandler.post {
            handle.notifyStateChanged(state)
        }
    }

    fun getHandle(taskId: String): TaskHandle<*>? = activeHandles[taskId]

    fun cancel(taskId: String): Boolean {
        // First try canceling in active running map
        val handle = activeHandles[taskId]
        if (handle != null) {
            handle.cancel()
            return true
        }
        
        // Next check if queued
        val removed = queue.cancel(taskId)
        if (removed != null) {
            triggerDispatch()
            return true
        }
        return false
    }

    fun clear() {
        for (handle in activeHandles.values) {
            handle.cancel()
        }
        activeHandles.clear()
        queue.clear()
    }
}
