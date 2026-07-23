package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.tasks.TaskScheduler

class ExportScheduler(
    private var context: Context?,
    private val queue: ExportQueue
) {
    private val taskScheduler = TaskScheduler.getInstance(context)

    fun submit(job: ExportJob) {
        Log.i("ExportScheduler", "Submitting Export Job to task scheduler: ${job.id}")
        
        // Register session as queued
        job.session.state = ExportState.QUEUED
        
        // TaskScheduler submit returns a TaskHandle which will execute asynchronously in the worker pool
        val handle = taskScheduler.submit(job)
        
        Log.d("ExportScheduler", "Export Job registered under Task ID: ${handle.taskId}")
    }

    fun cancel(sessionId: String) {
        val job = queue.getJob(sessionId)
        if (job != null) {
            Log.w("ExportScheduler", "Cancelling Export Session: $sessionId")
            job.cancelJob()
            taskScheduler.cancel(job.id)
            job.session.state = ExportState.CANCELLED
        }
    }

    fun pauseQueue() {
        // Since we execute via taskScheduler, we can pause or deprioritize remaining tasks in queue
        val sessions = queue.getAllSessions()
        for (session in sessions) {
            if (session.state == ExportState.QUEUED) {
                val job = queue.getJob(session.id)
                if (job != null) {
                    taskScheduler.cancel(job.id) // Cancel queued status
                    session.state = ExportState.IDLE
                }
            }
        }
        Log.i("ExportScheduler", "Export queue paused successfully.")
    }

    fun resumeQueue() {
        val sessions = queue.getAllSessions()
        for (session in sessions) {
            if (session.state == ExportState.IDLE) {
                val job = queue.getJob(session.id)
                if (job != null) {
                    submit(job)
                }
            }
        }
        Log.i("ExportScheduler", "Export queue resumed successfully.")
    }
}
