package com.litecut.app.timeline

import android.os.Handler
import android.os.Looper

class ProgressTracker(
    private val stats: ExportStatistics
) {
    interface ProgressListener {
        fun onProgress(progressPercent: Float, stats: ExportStatistics)
        fun onComplete(outputPath: String)
        fun onError(errorMsg: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<ProgressListener>()

    @Synchronized
    fun addListener(listener: ProgressListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    @Synchronized
    fun removeListener(listener: ProgressListener) {
        listeners.remove(listener)
    }

    fun notifyProgress(currentFrame: Int, totalFrames: Int, renderTimeMs: Long) {
        stats.updateMetrics(currentFrame, totalFrames, renderTimeMs)
        val percent = stats.getProgressPercentage()
        
        handler.post {
            synchronized(this) {
                for (listener in listeners) {
                    listener.onProgress(percent, stats)
                }
            }
        }
    }

    fun notifyComplete(outputPath: String) {
        handler.post {
            synchronized(this) {
                for (listener in listeners) {
                    listener.onComplete(outputPath)
                }
            }
        }
    }

    fun notifyError(errorMsg: String) {
        handler.post {
            synchronized(this) {
                for (listener in listeners) {
                    listener.onError(errorMsg)
                }
            }
        }
    }
}
