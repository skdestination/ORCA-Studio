package com.litecut.app.timeline.tasks

import kotlin.math.pow

sealed class RetryPolicy {
    abstract fun shouldRetry(attempt: Int): Boolean
    abstract fun getBackoffMs(attempt: Int): Long

    object NoRetry : RetryPolicy() {
        override fun shouldRetry(attempt: Int): Boolean = false
        override fun getBackoffMs(attempt: Int): Long = 0L
    }

    data class SimpleRetry(val maxAttempts: Int, val delayMs: Long) : RetryPolicy() {
        override fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts
        override fun getBackoffMs(attempt: Int): Long = delayMs
    }

    data class ExponentialBackoff(
        val maxAttempts: Int,
        val initialDelayMs: Long,
        val multiplier: Float = 2.0f
    ) : RetryPolicy() {
        override fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts
        override fun getBackoffMs(attempt: Int): Long {
            return (initialDelayMs * multiplier.toDouble().pow((attempt - 1).toDouble())).toLong()
        }
    }
}
