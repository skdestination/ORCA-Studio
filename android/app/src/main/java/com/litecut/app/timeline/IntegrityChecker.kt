package com.litecut.app.timeline

import android.util.Log
import java.io.File

class IntegrityChecker {

    /**
     * Scans all external asset references in the ProjectDocument to verify if they are linked or offline.
     */
    fun auditAssets(document: ProjectDocument): List<AssetReference> {
        val missingAssets = mutableListOf<AssetReference>()
        for (asset in document.assets) {
            val status = asset.checkStatus()
            if (status == AssetLinkStatus.OFFLINE) {
                missingAssets.add(asset)
            }
        }
        return missingAssets
    }

    /**
     * Searches a given search directory recursively to automatically find and relink missing assets.
     * Matches by exact name first, then validates file size or checksum if available.
     */
    fun attemptAutomaticRelinking(
        document: ProjectDocument,
        searchDirectory: File,
        onAssetRelinked: (AssetReference, String) -> Unit
    ): Int {
        if (!searchDirectory.exists() || !searchDirectory.isDirectory) {
            return 0
        }

        val missing = auditAssets(document)
        if (missing.isEmpty()) return 0

        var relinkedCount = 0
        val filesInFolder = scanDirectoryRecursively(searchDirectory)

        for (asset in missing) {
            val originalFile = File(asset.originalPath)
            val originalName = originalFile.name

            // Find matching files by name
            val matches = filesInFolder.filter { it.name.equals(originalName, ignoreCase = true) }
            
            for (match in matches) {
                // If asset size matches, or if checksum matches, we relink
                val sizeMatches = asset.fileSize <= 0L || asset.fileSize == match.length()
                if (sizeMatches) {
                    val newPath = match.absolutePath
                    val success = asset.relink(newPath)
                    if (success) {
                        relinkedCount++
                        onAssetRelinked(asset, newPath)
                        Log.i("IntegrityChecker", "Auto-relinked offline asset ${asset.assetName} to: $newPath")
                        break
                    }
                }
            }
        }

        return relinkedCount
    }

    private fun scanDirectoryRecursively(directory: File): List<File> {
        val fileList = mutableListOf<File>()
        val files = directory.listFiles() ?: return fileList
        for (file in files) {
            if (file.isDirectory) {
                fileList.addAll(scanDirectoryRecursively(file))
            } else {
                fileList.add(file)
            }
        }
        return fileList
    }
}
