package com.litecut.app.timeline.tasks

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TaskMonitor {
    val totalSubmitted = AtomicLong(0)
    val totalCompleted = AtomicLong(0)
    val totalFailed = AtomicLong(0)
    val totalCancelled = AtomicLong(0)
    val totalRetries = AtomicLong(0)

    private val totalExecutionTimeMs = AtomicLong(0)

    fun recordSubmission() {
        totalSubmitted.incrementAndGet()
    }

    fun recordCompletion(durationMs: Long) {
        totalCompleted.incrementAndGet()
        totalExecutionTimeMs.addAndGet(durationMs)
    }

    fun recordFailure() {
        totalFailed.incrementAndGet()
    }

    fun recordCancellation() {
        totalCancelled.incrementAndGet()
    }

    fun recordRetry() {
        totalRetries.incrementAndGet()
    }

    val averageExecutionTimeMs: Double
        get() {
            val completed = totalCompleted.get()
            return if (completed == 0L) 0.0 else totalExecutionTimeMs.get().toDouble() / completed.toDouble()
        }

    fun getStatsString(): String {
        return """
            ORCA TaskMonitor Analytics:
            - Total Submitted: ${totalSubmitted.get()}
            - Total Completed: ${totalCompleted.get()}
            - Total Failed: ${totalFailed.get()}
            - Total Cancelled: ${totalCancelled.get()}
            - Total Retries: ${totalRetries.get()}
            - Avg Run Time: ${String.format("%.1f", averageExecutionTimeMs)} ms
        """.trimIndent()
    }
}
