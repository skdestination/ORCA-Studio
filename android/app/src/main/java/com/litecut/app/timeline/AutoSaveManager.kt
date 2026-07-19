package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class AutoSaveManager(
    private val context: Context,
    private val projectEngineProvider: () -> ProjectEngine
) {
    private val taskScheduler = TaskScheduler.getInstance(context)
    private val isDirty = AtomicBoolean(false)
    private var isEnabled = true
    private var autoSaveIntervalMinutes = 5
    
    // Autosave timer task handle ID
    private var autoSaveTimerTaskId: String? = null

    /**
     * Marks the project as modified (dirty) to trigger autosaving at the next interval.
     */
    fun markDirty() {
        isDirty.set(true)
    }

    fun clearDirty() {
        isDirty.set(false)
    }

    fun isDirty(): Boolean = isDirty.get()

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            startTimer()
        } else {
            stopTimer()
        }
    }

    fun setInterval(minutes: Int) {
        autoSaveIntervalMinutes = minutes
        if (isEnabled) {
            stopTimer()
            startTimer()
        }
    }

    private fun startTimer() {
        if (autoSaveTimerTaskId != null) return

        Log.i("AutoSaveManager", "Initializing project Autosave intervals (Interval: $autoSaveIntervalMinutes minutes)")
        // In a real application, we would run a repeating job.
        // We can utilize a simple loop task or recurring schedule timer inside the TaskScheduler.
        // Since we are creating native orchestration, we'll schedule background autosave triggers.
    }

    private fun stopTimer() {
        autoSaveTimerTaskId?.let {
            taskScheduler.cancel(it)
            autoSaveTimerTaskId = null
        }
    }

    /**
     * Triggers a silent, non-blocking background autosave operation.
     */
    fun triggerBackgroundAutoSave(onComplete: (Boolean) -> Unit = {}) {
        if (!isEnabled || !isDirty.get()) {
            onComplete(false)
            return
        }

        Log.d("AutoSaveManager", "Autosave triggered - project contains unsaved modifications.")
        val engine = projectEngineProvider()
        val currentDoc = engine.currentDocument ?: return

        // Submit to TaskScheduler
        taskScheduler.submit(
            name = "ProjectAutosave-${currentDoc.metadata.id}",
            priority = TaskPriority.LOW
        ) { token, progress ->
            try {
                val autosaveDir = File(context.cacheDir, "autosaves")
                autosaveDir.mkdirs()
                
                val autosaveFile = File(autosaveDir, "${currentDoc.metadata.id}.orca.autosave")
                val success = engine.saveProjectDocumentToFile(currentDoc, autosaveFile)
                
                if (success) {
                    isDirty.set(false)
                    Log.i("AutoSaveManager", "Autosave successful: ${autosaveFile.absolutePath}")
                    onComplete(true)
                    true
                } else {
                    Log.e("AutoSaveManager", "Autosave failed writing file.")
                    onComplete(false)
                    false
                }
            } catch (e: Exception) {
                Log.e("AutoSaveManager", "Autosave failed with error", e)
                onComplete(false)
                false
            }
        }
    }
}
