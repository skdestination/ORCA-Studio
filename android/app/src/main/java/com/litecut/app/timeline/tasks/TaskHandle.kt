package com.litecut.app.timeline.tasks

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TaskHandle<T>(
    val taskId: String,
    val task: Task<T>
) {
    private val latch = CountDownLatch(1)
    private val listeners = CopyOnWriteArrayList<TaskProgressListener>()

    interface TaskProgressListener {
        fun onStateChanged(state: TaskState)
        fun onProgressUpdated(progress: Int)
    }

    val state: TaskState
        get() = task.state

    val isDone: Boolean
        get() = state == TaskState.COMPLETED || state == TaskState.CANCELLED || state == TaskState.FAILED

    val progress: Int
        get() = task.progress

    val elapsedTimeMs: Long
        get() {
            val start = task.startedAt
            val end = task.completedAt
            return if (start == 0L) {
                0L
            } else if (end == 0L) {
                System.currentTimeMillis() - start
            } else {
                end - start
            }
        }

    fun cancel() {
        task.cancellationToken.cancel()
        task.state = TaskState.CANCELLED
        notifyStateChanged(TaskState.CANCELLED)
        latch.countDown()
    }

    fun addListener(listener: TaskProgressListener) {
        listeners.add(listener)
        // Fire initial values
        listener.onStateChanged(task.state)
        listener.onProgressUpdated(task.progress)
    }

    fun removeListener(listener: TaskProgressListener) {
        listeners.remove(listener)
    }

    /**
     * Blocks the calling thread until the task completes, fails, or is cancelled.
     */
    fun join(): TaskResult<T> {
        latch.await()
        return task.result ?: TaskResult.Failure(IllegalStateException("Task finished with no result"))
    }

    /**
     * Blocks the calling thread with a timeout.
     */
    fun join(timeout: Long, unit: TimeUnit): TaskResult<T>? {
        val completed = latch.await(timeout, unit)
        return if (completed) {
            task.result ?: TaskResult.Failure(IllegalStateException("Task finished with no result"))
        } else {
            null
        }
    }

    internal fun complete(result: TaskResult<T>) {
        task.result = result
        latch.countDown()
    }

    internal fun notifyStateChanged(state: TaskState) {
        for (listener in listeners) {
            try {
                listener.onStateChanged(state)
            } catch (e: Exception) {}
        }
    }

    internal fun notifyProgressUpdated(progress: Int) {
        for (listener in listeners) {
            try {
                listener.onProgressUpdated(progress)
            } catch (e: Exception) {}
        }
    }
}
