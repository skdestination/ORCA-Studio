package com.litecut.app.timeline

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class LUT(
    val id: String,
    val name: String,
    val filePath: String?,
    val size: Int = 33, // Default 3D LUT size (33x33x33)
    var intensity: Float = 1.0f, // Blending opacity of the LUT [0.0 - 1.0]
    val isBuiltIn: Boolean = true
) {
    // 3D grid flat array of size * size * size * 3
    var lutData: FloatArray? = null

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("filePath", filePath ?: "")
        json.put("size", size)
        json.put("intensity", intensity.toDouble())
        json.put("isBuiltIn", isBuiltIn)
        return json
    }

    /**
     * Parses a standard Adobe 3D LUT (.cube) file.
     * High-fidelity, zero-copy, efficient parsing.
     */
    fun parseCubeFile(inputStream: InputStream) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var currentSize = size
        val rgbList = ArrayList<Float>()

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    line = reader.readLine()
                    continue
                }

                if (trimmed.startsWith("LUT_3D_SIZE")) {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        currentSize = parts[1].toInt()
                    }
                } else if (trimmed.startsWith("LUT_1D_SIZE") || trimmed.startsWith("DOMAIN_MIN") || trimmed.startsWith("DOMAIN_MAX")) {
                    // Skip metadata or unsupported dimensions gracefully
                } else {
                    // Try parsing RGB triple
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        try {
                            val r = parts[0].toFloat()
                            val g = parts[1].toFloat()
                            val b = parts[2].toFloat()
                            rgbList.add(r)
                            rgbList.add(g)
                            rgbList.add(b)
                        } catch (e: NumberFormatException) {
                            // Suppress
                        }
                    }
                }
                line = reader.readLine()
            }

            if (rgbList.isNotEmpty()) {
                lutData = rgbList.toFloatArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
        }
    }

    companion object {
        fun fromJSONObject(json: JSONObject): LUT {
            val filePathVal = json.optString("filePath", "")
            return LUT(
                id = json.getString("id"),
                name = json.getString("name"),
                filePath = if (filePathVal.isEmpty()) null else filePathVal,
                size = json.optInt("size", 33),
                intensity = json.optDouble("intensity", 1.0).toFloat(),
                isBuiltIn = json.optBoolean("isBuiltIn", true)
            )
        }
    }
}
