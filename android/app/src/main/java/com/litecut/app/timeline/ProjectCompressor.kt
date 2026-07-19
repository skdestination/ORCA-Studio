package com.litecut.app.timeline

import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class ProjectCompressor {

    /**
     * Compresses project JSON text into a binary GZIP package saved at the target File path.
     */
    fun compressToFile(projectJsonStr: String, outputFile: File): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                GZIPOutputStream(fos).use { gzos ->
                    gzos.write(projectJsonStr.toByteArray(StandardCharsets.UTF_8))
                }
            }
            Log.d("ProjectCompressor", "Successfully compressed project to ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("ProjectCompressor", "Failed to compress project JSON to file", e)
            false
        }
    }

    /**
     * Decompresses an `.orca` GZIP file back to its original JSON string representation.
     */
    fun decompressFromFile(inputFile: File): String? {
        if (!inputFile.exists() || !inputFile.isFile) {
            Log.e("ProjectCompressor", "File does not exist: ${inputFile.absolutePath}")
            return null
        }
        return try {
            FileInputStream(inputFile).use { fis ->
                GZIPInputStream(fis).use { gzis ->
                    BufferedReader(InputStreamReader(gzis, StandardCharsets.UTF_8)).use { reader ->
                        val sb = java.lang.StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            sb.append(line).append("\n")
                        }
                        sb.toString()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ProjectCompressor", "Failed to decompress project from file", e)
            null
        }
    }

    /**
     * In-memory GZIP compression utility.
     */
    fun compressToBytes(projectJsonStr: String): ByteArray? {
        return try {
            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gzos ->
                gzos.write(projectJsonStr.toByteArray(StandardCharsets.UTF_8))
            }
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e("ProjectCompressor", "Failed in-memory GZIP compression", e)
            null
        }
    }

    /**
     * In-memory GZIP decompression utility.
     */
    fun decompressFromBytes(bytes: ByteArray): String? {
        return try {
            val bais = ByteArrayInputStream(bytes)
            GZIPInputStream(bais).use { gzis ->
                BufferedReader(InputStreamReader(gzis, StandardCharsets.UTF_8)).use { reader ->
                    val sb = java.lang.StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                    sb.toString()
                }
            }
        } catch (e: Exception) {
            Log.e("ProjectCompressor", "Failed in-memory GZIP decompression", e)
            null
        }
    }
}
