package com.litecut.app.timeline

class QueueExportCommand(
    private val session: ExportSession,
    private val job: ExportJob
) : Command {
    override fun execute(engine: TimelineEngine) {
        val exportEngine = ExportEngine.getInstance()
        exportEngine.queue.addSession(session, job)
        exportEngine.scheduler.submit(job)
    }

    override fun undo(engine: TimelineEngine) {
        val exportEngine = ExportEngine.getInstance()
        exportEngine.scheduler.cancel(session.id)
        exportEngine.queue.removeSession(session.id)
    }
}

class CancelExportCommand(
    private val sessionId: String
) : Command {
    private var cachedSession: ExportSession? = null
    private var cachedJob: ExportJob? = null

    override fun execute(engine: TimelineEngine) {
        val exportEngine = ExportEngine.getInstance()
        cachedSession = exportEngine.queue.getSession(sessionId)
        cachedJob = exportEngine.queue.getJob(sessionId)
        
        exportEngine.scheduler.cancel(sessionId)
    }

    override fun undo(engine: TimelineEngine) {
        val exportEngine = ExportEngine.getInstance()
        val s = cachedSession
        val j = cachedJob
        if (s != null && j != null) {
            exportEngine.queue.addSession(s, j)
            exportEngine.scheduler.submit(j)
        }
    }
}
