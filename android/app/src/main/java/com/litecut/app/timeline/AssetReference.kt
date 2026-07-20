package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

enum class AssetLinkStatus {
    LINKED,
    OFFLINE
}

data class AssetReference(
    val id: String,
    var filePath: String,
    var originalPath: String = filePath,
    var assetName: String = File(filePath).name,
    var fileSize: Long = 0L,
    var checksum: String = "",
    var type: AssetType = AssetType.UNKNOWN,
    var width: Int = 0,
    var height: Int = 0,
    var durationMs: Long = 0L,
    var status: AssetLinkStatus = AssetLinkStatus.LINKED
) {
    fun checkStatus(): AssetLinkStatus {
        val file = File(filePath)
        status = if (file.exists() && file.isFile) {
            AssetLinkStatus.LINKED
        } else {
            AssetLinkStatus.OFFLINE
        }
        return status
    }

    fun calculateChecksum(): String {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return ""
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hashBytes = digest.digest()
            val sb = StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            checksum = sb.toString()
            checksum
        } catch (e: Exception) {
            Log.e("AssetReference", "Failed to compute checksum for asset $filePath", e)
            ""
        }
    }

    fun relink(newPath: String): Boolean {
        val file = File(newPath)
        if (file.exists() && file.isFile) {
            filePath = newPath
            fileSize = file.length()
            checkStatus()
            return true
        }
        return false
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("filePath", filePath)
        json.put("originalPath", originalPath)
        json.put("assetName", assetName)
        json.put("fileSize", fileSize)
        json.put("checksum", checksum)
        json.put("type", type.name)
        json.put("width", width)
        json.put("height", height)
        json.put("durationMs", durationMs)
        json.put("status", status.name)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): AssetReference {
            return AssetReference(
                id = json.getString("id"),
                filePath = json.getString("filePath"),
                originalPath = json.optString("originalPath", json.getString("filePath")),
                assetName = json.optString("assetName", ""),
                fileSize = json.optLong("fileSize", 0L),
                checksum = json.optString("checksum", ""),
                type = AssetType.valueOf(json.optString("type", AssetType.UNKNOWN.name)),
                width = json.optInt("width", 0),
                height = json.optInt("height", 0),
                durationMs = json.optLong("durationMs", 0L),
                status = AssetLinkStatus.valueOf(json.optString("status", AssetLinkStatus.LINKED.name))
            )
        }
    }
}
