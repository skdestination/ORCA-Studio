package com.litecut.app.timeline.tasks

import android.content.Context
import android.util.Log
import java.util.UUID

class TaskScheduler private constructor(context: Context) {

    private val queue = TaskQueue()
    private val workerPool = WorkerPool()
    val monitor = TaskMonitor()
    
    private val dispatcher = TaskDispatcher(context, queue, workerPool)

    companion object {
        @Volatile
        private var instance: TaskScheduler? = null

        fun getInstance(context: Context? = null): TaskScheduler {
            return instance ?: synchronized(this) {
                instance ?: if (context != null) {
                    TaskScheduler(context.applicationContext).also { instance = it }
                } else {
                    throw IllegalStateException("TaskScheduler is not initialized. Please pass a valid Context first.")
                }
            }
        }
    }

    /**
     * Submit a subclassed Task instance.
     */
    fun <T> submit(task: Task<T>): TaskHandle<T> {
        monitor.recordSubmission()
        
        val handle = dispatcher.submit(task)
        
        // Setup listener to feed metrics to TaskMonitor
        handle.addListener(object : TaskHandle.TaskProgressListener {
            override fun onStateChanged(state: TaskState) {
                when (state) {
                    TaskState.COMPLETED -> {
                        monitor.recordCompletion(handle.elapsedTimeMs)
                    }
                    TaskState.FAILED -> {
                        monitor.recordFailure()
                    }
                    TaskState.CANCELLED -> {
                        monitor.recordCancellation()
                    }
                    TaskState.RETRYING -> {
                        monitor.recordRetry()
                    }
                    else -> {}
                }
            }

            override fun onProgressUpdated(progress: Int) {}
        })

        return handle
    }

    /**
     * Submit a flexible inline background lambda block as a Task.
     */
    fun <T> submit(
        name: String,
        priority: TaskPriority = TaskPriority.NORMAL,
        retryPolicy: RetryPolicy = RetryPolicy.NoRetry,
        block: (CancellationToken, (Int) -> Unit) -> T
    ): TaskHandle<T> {
        val taskId = UUID.randomUUID().toString()
        val taskInstance = object : Task<T>(taskId, name, priority, retryPolicy) {
            override fun execute(token: CancellationToken, onProgressUpdate: (Int) -> Unit): T {
                return block(token, onProgressUpdate)
            }
        }
        return submit(taskInstance)
    }

    /**
     * Cancels an active or queued task.
     */
    fun cancel(taskId: String): Boolean {
        return dispatcher.cancel(taskId)
    }

    /**
     * Change priority of a queued task dynamically.
     */
    fun updatePriority(taskId: String, newPriority: TaskPriority): Boolean {
        val updated = queue.updatePriority(taskId, newPriority)
        if (updated) {
            dispatcher.triggerDispatch()
        }
        return updated
    }

    /**
     * Retrieve the current list of queued tasks.
     */
    fun getQueuedTasks(): List<Task<*>> {
        return queue.getTasksSnapshot()
    }

    /**
     * Retrieve the active status of the worker pool.
     */
    fun getActiveWorkerCount(): Int {
        return workerPool.getActiveCount()
    }

    /**
     * Retrieve the standard core size of background workers.
     */
    fun getCoreWorkerCount(): Int {
        return workerPool.getCorePoolSize()
    }

    /**
     * Force clear all queued and executing operations.
     */
    fun clear() {
        dispatcher.clear()
    }

    /**
     * Fully shutdown the scheduler system.
     */
    fun shutdown() {
        clear()
        workerPool.shutdown()
        Log.i("TaskScheduler", "ORCA TaskScheduler shutdown complete.")
    }
}
