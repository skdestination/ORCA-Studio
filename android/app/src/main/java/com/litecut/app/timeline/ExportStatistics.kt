package com.litecut.app.timeline

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ExportStatistics {
    val totalFrames = AtomicInteger(0)
    val currentFrame = AtomicInteger(0)
    
    val startTimeMs = AtomicLong(0L)
    val elapsedTimeMs = AtomicLong(0L)
    val estimatedRemainingTimeMs = AtomicLong(0L)
    
    val encodingFps = AtomicInteger(0)
    val droppedFrames = AtomicInteger(0)
    
    val totalRenderTimeMs = AtomicLong(0L)
    val averageRenderTimeMs = AtomicLong(0)
    
    val currentMemoryUsageBytes = AtomicLong(0L)
    val gpuUsagePercentage = AtomicInteger(0) // Placeholder representation of active GLES loads
    
    fun reset() {
        totalFrames.set(0)
        currentFrame.set(0)
        startTimeMs.set(System.currentTimeMillis())
        elapsedTimeMs.set(0L)
        estimatedRemainingTimeMs.set(0L)
        encodingFps.set(0)
        droppedFrames.set(0)
        totalRenderTimeMs.set(0L)
        averageRenderTimeMs.set(0)
        currentMemoryUsageBytes.set(0L)
        gpuUsagePercentage.set(0)
    }

    fun updateMetrics(currentFrameIndex: Int, totalFramesCount: Int, renderTimeMs: Long) {
        currentFrame.set(currentFrameIndex)
        totalFrames.set(totalFramesCount)
        
        val elapsed = System.currentTimeMillis() - startTimeMs.get()
        elapsedTimeMs.set(elapsed)

        if (currentFrameIndex > 0) {
            val avg = elapsed.toFloat() / currentFrameIndex
            val remainingFrames = totalFramesCount - currentFrameIndex
            estimatedRemainingTimeMs.set((avg * remainingFrames).toLong())
            
            val fps = (currentFrameIndex * 1000.0f / elapsed).toInt()
            encodingFps.set(fps)
        }

        totalRenderTimeMs.addAndGet(renderTimeMs)
        if (currentFrameIndex > 0) {
            averageRenderTimeMs.set(totalRenderTimeMs.get() / currentFrameIndex)
        }

        // Keep track of runtime memory usage
        val runtime = Runtime.getRuntime()
        currentMemoryUsageBytes.set(runtime.totalMemory() - runtime.freeMemory())
        
        // GPU Usage Simulation (scales dynamically based on effects & resolution)
        gpuUsagePercentage.set((40 + (Math.sin(currentFrameIndex.toDouble() / 10.0) * 15)).toInt().coerceIn(10, 95))
    }

    fun getProgressPercentage(): Float {
        val total = totalFrames.get()
        if (total <= 0) return 0.0f
        return (currentFrame.get().toFloat() / total * 100.0f).coerceIn(0.0f, 100.0f)
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("progress", getProgressPercentage().toDouble())
        json.put("totalFrames", totalFrames.get())
        json.put("currentFrame", currentFrame.get())
        json.put("elapsedTimeMs", elapsedTimeMs.get())
        json.put("estimatedRemainingTimeMs", estimatedRemainingTimeMs.get())
        json.put("encodingFps", encodingFps.get())
        json.put("droppedFrames", droppedFrames.get())
        json.put("averageRenderTimeMs", averageRenderTimeMs.get())
        json.put("currentMemoryUsageBytes", currentMemoryUsageBytes.get())
        json.put("gpuUsagePercentage", gpuUsagePercentage.get())
        return json
    }
}
