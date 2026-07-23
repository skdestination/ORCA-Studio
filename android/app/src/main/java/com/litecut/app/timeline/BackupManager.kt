package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context) {

    companion object {
        private const val MAX_BACKUPS = 5
        private const val TAG = "BackupManager"
    }

    /**
     * Creates a time-stamped historical backup of the specified project file.
     * Keeps up to MAX_BACKUPS historical copies, purging older ones.
     */
    fun createProjectBackup(projectFile: File): File? {
        if (!projectFile.exists() || !projectFile.isFile) {
            return null
        }

        try {
            val ctx = context ?: ApplicationContextProvider.context ?: return null
            val backupDir = File(ctx.filesDir, "project_backups/${projectFile.nameWithoutExtension}")
            backupDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "${projectFile.nameWithoutExtension}_backup_$timestamp.orca")

            projectFile.copyTo(backupFile, overwrite = true)
            Log.i(TAG, "Created project backup: ${backupFile.absolutePath}")

            pruneOldBackups(backupDir)
            return backupFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create project backup", e)
            return null
        }
    }

    /**
     * Checks if a crash recovery scenario exists.
     * Occurs if the cached autosave file has a newer modification timestamp than the original project file.
     */
    fun checkForCrashRecovery(projectFile: File, projectId: String): File? {
        val autosaveDir = File(context.cacheDir, "autosaves")
        val autosaveFile = File(autosaveDir, "$projectId.orca.autosave")

        if (autosaveFile.exists() && autosaveFile.isFile) {
            if (!projectFile.exists()) {
                // Project was never saved but autosave exists - definite recovery candidate!
                return autosaveFile
            }
            if (autosaveFile.lastModified() > projectFile.lastModified()) {
                Log.w(TAG, "Crash recovery detected! Autosave is newer than original file.")
                return autosaveFile
            }
        }
        return null
    }

    /**
     * Restores a project from a specified backup or autosave file.
     */
    fun restoreFromBackup(backupFile: File, targetFile: File): Boolean {
        return try {
            if (!backupFile.exists() || !backupFile.isFile) {
                return false
            }
            targetFile.parentFile?.mkdirs()
            backupFile.copyTo(targetFile, overwrite = true)
            Log.i(TAG, "Successfully restored project to: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore project from backup", e)
            false
        }
    }

    /**
     * Retains only the most recent historical backups up to MAX_BACKUPS.
     */
    private fun pruneOldBackups(backupDir: File) {
        val backups = backupDir.listFiles { file -> file.isFile && file.name.endsWith(".orca") } ?: return
        if (backups.size > MAX_BACKUPS) {
            // Sort by age (oldest first)
            val sortedBackups = backups.sortedBy { it.lastModified() }
            val itemsToDelete = sortedBackups.size - MAX_BACKUPS
            for (i in 0 until itemsToDelete) {
                val oldFile = sortedBackups[i]
                if (oldFile.delete()) {
                    Log.d(TAG, "Pruned old backup copy: ${oldFile.name}")
                }
            }
        }
    }
}
