package com.litecut.app.timeline

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.litecut.app.timeline.tasks.CancellationToken
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import java.io.File

class AssetScanner(private val context: Context) {

    companion object {
        private const val TAG = "AssetScanner"
    }

    /**
     * Scans a local directory recursively for media files.
     * Can run in the background via the TaskScheduler to prevent blocking the UI.
     */
    fun scanDirectory(
        directory: File,
        priority: TaskPriority = TaskPriority.NORMAL,
        onProgress: (Int) -> Unit = {},
        onComplete: (List<File>) -> Unit
    ) {
        val scheduler = TaskScheduler.getInstance(context)
        scheduler.submit(
            name = "FolderScan-${directory.name}",
            priority = priority
        ) { token, progressCallback ->
            val results = mutableListOf<File>()
            try {
                scanDirRecursive(directory, results, token)
                onComplete(results)
            } catch (e: Exception) {
                Log.e(TAG, "Failed scanning directory recursive", e)
                onComplete(emptyList())
            }
            true
        }
    }

    private fun scanDirRecursive(file: File, results: MutableList<File>, token: CancellationToken) {
        if (token.isCancelled()) return

        if (file.isDirectory) {
            val list = file.listFiles() ?: return
            for (f in list) {
                scanDirRecursive(f, results, token)
            }
        } else if (file.isFile && isSupportedMediaFile(file)) {
            results.add(file)
        }
    }

    /**
     * Determines whether the file extension represents a supported media, font, or LUT asset.
     */
    fun isSupportedMediaFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return when (ext) {
            "mp4", "mkv", "mov", "webm", "3gp", "avi" -> true // Video
            "mp3", "wav", "m4a", "ogg", "aac", "flac" -> true // Audio
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> true // Image
            "ttf", "otf", "woff", "woff2" -> true // Fonts
            "cube" -> true // LUT
            "json" -> true // Could be sticker/text templates
            else -> false
        }
    }

    /**
     * Detects and maps file extension to the appropriate AssetType category.
     */
    fun detectAssetType(file: File): AssetType {
        val ext = file.extension.lowercase()
        return when (ext) {
            "mp4", "mov", "mkv", "webm", "3gp", "avi" -> AssetType.VIDEO
            "mp3", "wav", "m4a", "ogg", "aac", "flac" -> AssetType.AUDIO
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> AssetType.IMAGE
            "ttf", "otf", "woff", "woff2" -> AssetType.FONT
            "cube" -> AssetType.LUT
            else -> AssetType.UNKNOWN
        }
    }

    /**
     * Queries the Android MediaStore to scan and import device videos or audios.
     */
    fun scanMediaStore(uri: Uri): List<String> {
        val filePaths = mutableListOf<String>()
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        
        cursor?.use {
            val dataIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (it.moveToNext()) {
                val path = it.getString(dataIndex)
                if (path != null && File(path).exists()) {
                    filePaths.add(path)
                }
            }
        }
        return filePaths
    }

    /**
     * Helper to resolve Storage Access Framework (SAF) document URIs back to file paths.
     */
    fun resolveSafUri(uri: Uri): String? {
        // Standard document provider resolution or direct projection
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, projection, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (columnIndex != -1) {
                        return cursor.getString(columnIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed resolving SAF URI projection", e)
            } finally {
                cursor?.close()
            }
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }
}
