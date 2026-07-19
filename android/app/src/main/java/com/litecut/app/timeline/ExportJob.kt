package com.litecut.app.timeline

import com.litecut.app.timeline.tasks.CancellationToken
import com.litecut.app.timeline.tasks.RetryPolicy
import com.litecut.app.timeline.tasks.Task
import com.litecut.app.timeline.tasks.TaskPriority

class ExportJob(
    val session: ExportSession,
    private val pipeline: ExportPipeline
) : Task<Boolean>(
    id = session.id,
    name = "Export-${session.id}",
    priority = TaskPriority.HIGH, // High priority because it is an export operation
    retryPolicy = RetryPolicy.NoRetry
) {
    override fun execute(token: CancellationToken, onProgressUpdate: (Int) -> Unit): Boolean {
        session.state = ExportState.EXPORTING
        session.startTimeMs = System.currentTimeMillis()
        
        // Listen to progress updates
        pipeline.progressTracker.addListener(object : ProgressTracker.ProgressListener {
            override fun onProgress(progressPercent: Float, stats: ExportStatistics) {
                onProgressUpdate(progressPercent.toInt())
                session.stats.updateMetrics(
                    stats.currentFrame.get(),
                    stats.totalFrames.get(),
                    stats.averageRenderTimeMs.get()
                )
            }

            override fun onComplete(outputPath: String) {
                session.state = ExportState.COMPLETED
                session.endTimeMs = System.currentTimeMillis()
            }

            override fun onError(errorMsg: String) {
                session.state = if (errorMsg.contains("Cancel", ignoreCase = true)) {
                    ExportState.CANCELLED
                } else {
                    ExportState.FAILED
                }
                session.errorMsg = errorMsg
                session.endTimeMs = System.currentTimeMillis()
            }
        })

        val success = pipeline.execute(token)
        
        if (!success) {
            if (token.isCancelled) {
                session.state = ExportState.CANCELLED
            } else {
                session.state = ExportState.FAILED
            }
        } else {
            session.state = ExportState.COMPLETED
        }
        
        return success
    }

    fun cancelJob() {
        pipeline.cancel()
        cancellationToken.cancel()
        session.state = ExportState.CANCELLED
    }
}
