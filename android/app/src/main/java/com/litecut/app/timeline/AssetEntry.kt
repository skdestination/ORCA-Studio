package com.litecut.app.timeline

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AssetEntry(
    val id: String,
    var filePath: String,
    var originalPath: String = filePath,
    var assetName: String = File(filePath).name,
    var fileSize: Long = 0L,
    var checksum: String = "",
    var type: AssetType = AssetType.UNKNOWN,
    var customTypeName: String? = null,
    var isFavorite: Boolean = false,
    val tags: MutableSet<String> = mutableSetOf(),
    val collections: MutableSet<String> = mutableSetOf(),
    val importDateMs: Long = System.currentTimeMillis(),
    var colorLabel: Int = 0,
    var usageCount: Int = 0,
    var isPinned: Boolean = false,
    val metadata: MutableMap<String, Any?> = mutableMapOf()
) {
    // --- Video Metadata Getters ---
    val videoWidth: Int get() = (metadata["video_width"] as? Number)?.toInt() ?: 0
    val videoHeight: Int get() = (metadata["video_height"] as? Number)?.toInt() ?: 0
    val videoFps: Float get() = (metadata["video_fps"] as? Number)?.toFloat() ?: 0.0f
    val videoCodec: String get() = metadata["video_codec"] as? String ?: "unknown"
    val videoBitrate: Long get() = (metadata["video_bitrate"] as? Number)?.toLong() ?: 0L
    val videoDurationMs: Long get() = (metadata["video_duration_ms"] as? Number)?.toLong() ?: 0L
    val videoRotation: Int get() = (metadata["video_rotation"] as? Number)?.toInt() ?: 0
    val videoColorSpace: String get() = metadata["video_color_space"] as? String ?: "SDR"
    val videoIsHDR: Boolean get() = metadata["video_is_hdr"] as? Boolean ?: false

    // --- Audio Metadata Getters ---
    val audioDurationMs: Long get() = (metadata["audio_duration_ms"] as? Number)?.toLong() ?: 0L
    val audioSampleRate: Int get() = (metadata["audio_sample_rate"] as? Number)?.toInt() ?: 0
    val audioChannels: Int get() = (metadata["audio_channels"] as? Number)?.toInt() ?: 0
    val audioBitDepth: Int get() = (metadata["audio_bit_depth"] as? Number)?.toInt() ?: 16
    val audioCodec: String get() = metadata["audio_codec"] as? String ?: "unknown"
    val audioLoudness: Float get() = (metadata["audio_loudness"] as? Number)?.toFloat() ?: 0.0f

    // --- Image Metadata Getters ---
    val imageWidth: Int get() = (metadata["image_width"] as? Number)?.toInt() ?: 0
    val imageHeight: Int get() = (metadata["image_height"] as? Number)?.toInt() ?: 0
    val exifOrientation: Int get() = (metadata["exif_orientation"] as? Number)?.toInt() ?: 0
    val imageColorProfile: String get() = metadata["image_color_profile"] as? String ?: "sRGB"

    // --- Font Metadata Getters ---
    val fontFamily: String get() = metadata["font_family"] as? String ?: "default"
    val fontStyle: String get() = metadata["font_style"] as? String ?: "regular"
    val fontWeight: Int get() = (metadata["font_weight"] as? Number)?.toInt() ?: 400

    // --- LUT Metadata Getters ---
    val lutCubeSize: Int get() = (metadata["lut_cube_size"] as? Number)?.toInt() ?: 0
    val lutDomainMin: Float get() = (metadata["lut_domain_min"] as? Number)?.toFloat() ?: 0.0f
    val lutDomainMax: Float get() = (metadata["lut_domain_max"] as? Number)?.toFloat() ?: 1.0f

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("filePath", filePath)
        json.put("originalPath", originalPath)
        json.put("assetName", assetName)
        json.put("fileSize", fileSize)
        json.put("checksum", checksum)
        json.put("type", type.name)
        customTypeName?.let { json.put("customTypeName", it) }
        json.put("isFavorite", isFavorite)
        
        val tagsArray = JSONArray()
        tags.forEach { tagsArray.put(it) }
        json.put("tags", tagsArray)

        val collectionsArray = JSONArray()
        collections.forEach { collectionsArray.put(it) }
        json.put("collections", collectionsArray)

        json.put("importDateMs", importDateMs)
        json.put("colorLabel", colorLabel)
        json.put("usageCount", usageCount)
        json.put("isPinned", isPinned)

        val metaJson = JSONObject()
        for ((k, v) in metadata) {
            metaJson.put(k, v)
        }
        json.put("metadata", metaJson)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): AssetEntry {
            val entry = AssetEntry(
                id = json.getString("id"),
                filePath = json.getString("filePath"),
                originalPath = json.optString("originalPath", json.getString("filePath")),
                assetName = json.optString("assetName", ""),
                fileSize = json.optLong("fileSize", 0L),
                checksum = json.optString("checksum", ""),
                type = AssetType.valueOf(json.optString("type", AssetType.UNKNOWN.name)),
                customTypeName = json.optString("customTypeName", null),
                isFavorite = json.optBoolean("isFavorite", false),
                importDateMs = json.optLong("importDateMs", System.currentTimeMillis()),
                colorLabel = json.optInt("colorLabel", 0),
                usageCount = json.optInt("usageCount", 0),
                isPinned = json.optBoolean("isPinned", false)
            )

            val tagsArray = json.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    entry.tags.add(tagsArray.getString(i))
                }
            }

            val collsArray = json.optJSONArray("collections")
            if (collsArray != null) {
                for (i in 0 until collsArray.length()) {
                    entry.collections.add(collsArray.getString(i))
                }
            }

            val metaJson = json.optJSONObject("metadata")
            if (metaJson != null) {
                val keys = metaJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    entry.metadata[key] = metaJson.get(key)
                }
            }

            return entry
        }
    }
}
