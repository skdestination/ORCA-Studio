package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.resources.ResourceManager
import java.util.UUID

class ExportEngine private constructor(
    private val context: Context,
    private val timelineEngine: TimelineEngine
) {
    val queue = ExportQueue()
    val scheduler = ExportScheduler(context, queue)
    private val resourceManager = ResourceManager.getInstance(context)

    companion object {
        @Volatile
        private var instance: ExportEngine? = null

        fun getInstance(timelineEngine: TimelineEngine, context: Context? = null): ExportEngine {
            return instance ?: synchronized(this) {
                instance ?: if (context != null) {
                    ExportEngine(context.applicationContext, timelineEngine).also { instance = it }
                } else {
                    throw IllegalStateException("ExportEngine is not initialized. Please pass a valid Context first.")
                }
            }
        }
    }

    /**
     * Creates and schedules a new export session for the timeline project.
     * Keeps memory allocations at zero during the rendering loops.
     */
    fun startExport(
        profile: ExportProfile,
        outputPath: String
    ): ExportSession {
        // Enforce systemic memory limits prior to launching heavy rendering pipelines
        resourceManager.checkAndEnforceBudget("video_export_buffers")

        val sessionId = "export-${UUID.randomUUID()}"
        val settings = profile.createSettings(sessionId)
        
        val session = ExportSession(
            id = sessionId,
            settings = settings,
            outputPath = outputPath
        )

        val pipeline = ExportPipeline(context, timelineEngine, settings, outputPath)
        val job = ExportJob(session, pipeline)

        val command = QueueExportCommand(session, job)
        timelineEngine.executeCommand(command)

        Log.i("ExportEngine", "Scheduled export session: $sessionId with profile: ${profile.name}")
        return session
    }

    fun cancelExport(sessionId: String) {
        val command = CancelExportCommand(sessionId)
        timelineEngine.executeCommand(command)
    }

    fun getActiveSessions(): List<ExportSession> {
        return queue.getAllSessions()
    }
}
