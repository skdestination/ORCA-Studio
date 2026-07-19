package com.litecut.app.timeline.tasks

import android.util.Log
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class WorkerPool {
    private val cpuCores = Runtime.getRuntime().availableProcessors()
    private val corePoolSize = max(2, cpuCores - 1) // Leave one core for UI thread comfort
    
    private val threadCount = AtomicInteger(1)
    
    private val executor = ScheduledThreadPoolExecutor(
        corePoolSize,
        ThreadFactory { runnable ->
            Thread(runnable, "orca-worker-${threadCount.getAndIncrement()}").apply {
                // Set slightly lower than normal priority to keep UI buttery smooth
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    ).apply {
        // Allow core threads to timeout if inactive to save battery on long idling sessions
        setKeepAliveTime(15, TimeUnit.SECONDS)
        allowCoreThreadTimeOut(true)
    }

    init {
        Log.i("WorkerPool", "ORCA WorkerPool initialized with $corePoolSize background worker threads (CPU Cores: $cpuCores).")
    }

    fun execute(runnable: Runnable) {
        executor.execute(runnable)
    }

    fun schedule(runnable: Runnable, delayMs: Long) {
        executor.schedule(runnable, delayMs, TimeUnit.MILLISECONDS)
    }

    fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }

    fun getActiveCount(): Int = executor.activeCount
    fun getCorePoolSize(): Int = corePoolSize
}
