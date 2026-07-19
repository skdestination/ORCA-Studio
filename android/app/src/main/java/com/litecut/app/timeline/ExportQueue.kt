package com.litecut.app.timeline

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ExportQueue {
    private val sessions = ConcurrentHashMap<String, ExportSession>()
    private val jobs = ConcurrentHashMap<String, ExportJob>()
    private val queueOrder = CopyOnWriteArrayList<String>()

    @Synchronized
    fun addSession(session: ExportSession, job: ExportJob) {
        sessions[session.id] = session
        jobs[session.id] = job
        queueOrder.add(session.id)
        session.state = ExportState.QUEUED
    }

    @Synchronized
    fun getSession(sessionId: String): ExportSession? = sessions[sessionId]

    @Synchronized
    fun getJob(sessionId: String): ExportJob? = jobs[sessionId]

    @Synchronized
    fun getAllSessions(): List<ExportSession> {
        return queueOrder.mapNotNull { sessions[it] }
    }

    @Synchronized
    fun removeSession(sessionId: String) {
        val job = jobs[sessionId]
        if (job != null) {
            job.cancelJob()
        }
        jobs.remove(sessionId)
        sessions.remove(sessionId)
        queueOrder.remove(sessionId)
    }

    @Synchronized
    fun clear() {
        for (job in jobs.values) {
            job.cancelJob()
        }
        jobs.clear()
        sessions.clear()
        queueOrder.clear()
    }
}
