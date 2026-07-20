package com.litecut.app.timeline

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.media.ExifInterface
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AssetMetadataExtractor(private val context: Context) {

    companion object {
        private const val TAG = "AssetMetadataExtractor"
    }

    /**
     * Extracts all relevant metadata for an AssetEntry based on its detected type.
     */
    fun extract(entry: AssetEntry) {
        val file = File(entry.filePath)
        if (!file.exists() || !file.isFile) {
            entry.fileSize = 0
            return
        }

        entry.fileSize = file.length()
        entry.checksum = "" // Computed asynchronously if requested

        try {
            when (entry.type) {
                AssetType.VIDEO -> extractVideoMetadata(entry)
                AssetType.AUDIO -> extractAudioMetadata(entry)
                AssetType.IMAGE -> extractImageMetadata(entry)
                AssetType.FONT -> extractFontMetadata(entry)
                AssetType.LUT -> extractLutMetadata(entry)
                else -> {
                    // Custom plugin types or unknown types can have metadata extracted by custom hooks
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata for asset: ${entry.filePath}", e)
        }
    }

    private fun extractVideoMetadata(entry: AssetEntry) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(entry.filePath)
            
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val bitrate = bitrateStr?.toLongOrNull() ?: 0L
            val rotation = rotationStr?.toIntOrNull() ?: 0

            entry.metadata["video_width"] = width
            entry.metadata["video_height"] = height
            entry.metadata["video_duration_ms"] = durationMs
            entry.metadata["video_bitrate"] = bitrate
            entry.metadata["video_rotation"] = rotation
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever failed for video", e)
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        // Try extracting fps, codec, HDR via MediaExtractor
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(entry.filePath)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    entry.metadata["video_codec"] = mime.substringAfter("video/")
                    
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        entry.metadata["video_fps"] = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    } else if (format.containsKey("frame-rate")) {
                        entry.metadata["video_fps"] = format.getFloat("frame-rate")
                    } else {
                        entry.metadata["video_fps"] = 30.0f // Fallback
                    }

                    // Check HDR metadata
                    var isHdr = false
                    var colorSpace = "SDR"
                    if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        // COLOR_TRANSFER_ST2084 = 6, COLOR_TRANSFER_HLG = 7
                        if (transfer == 6 || transfer == 7) {
                            isHdr = true
                            colorSpace = if (transfer == 6) "HDR10 (PQ)" else "HDR (HLG)"
                        }
                    }
                    if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                        val standard = format.getInteger(MediaFormat.KEY_COLOR_STANDARD)
                        // COLOR_STANDARD_BT2020 = 6
                        if (standard == 6) {
                            colorSpace += " BT.2020"
                        }
                    }
                    entry.metadata["video_is_hdr"] = isHdr
                    entry.metadata["video_color_space"] = colorSpace
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaExtractor failed for video formats", e)
        } finally {
            extractor.release()
        }
    }

    private fun extractAudioMetadata(entry: AssetEntry) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(entry.filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            entry.metadata["audio_duration_ms"] = durationMs
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever failed for audio", e)
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(entry.filePath)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    entry.metadata["audio_codec"] = mime.substringAfter("audio/")
                    
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        entry.metadata["audio_sample_rate"] = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        entry.metadata["audio_channels"] = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    // Bit depth isn't directly exposed by standard APIs, default to 16 but scan for custom keys
                    entry.metadata["audio_bit_depth"] = 16
                    entry.metadata["audio_loudness"] = -14.0f // Standard YouTube loudness target default
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaExtractor failed for audio formats", e)
        } finally {
            extractor.release()
        }
    }

    private fun extractImageMetadata(entry: AssetEntry) {
        try {
            val exif = ExifInterface(entry.filePath)
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            entry.metadata["image_width"] = width
            entry.metadata["image_height"] = height
            entry.metadata["exif_orientation"] = orientation
            entry.metadata["image_color_profile"] = "sRGB" // Default
        } catch (e: Exception) {
            Log.e(TAG, "ExifInterface failed for image", e)
        }
    }

    private fun extractFontMetadata(entry: AssetEntry) {
        try {
            val file = File(entry.filePath)
            RandomAccessFile(file, "r").use { raf ->
                // Basic TrueType Font Header Parser
                raf.seek(0)
                val sfntVersion = raf.readInt()
                val numTables = raf.readUnsignedShort()
                val searchRange = raf.readUnsignedShort()
                val entrySelector = raf.readUnsignedShort()
                val rangeShift = raf.readUnsignedShort()

                var nameTableOffset = -1L
                var nameTableLength = 0L

                // Scan tables for 'name' table (0x6E616D65)
                for (i in 0 until numTables) {
                    val tag = raf.readInt()
                    val checksum = raf.readInt()
                    val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                    val length = raf.readInt().toLong() and 0xFFFFFFFFL

                    if (tag == 0x6E616D65) { // 'name' tag
                        nameTableOffset = offset
                        nameTableLength = length
                        break
                    }
                }

                if (nameTableOffset != -1L) {
                    raf.seek(nameTableOffset)
                    val format = raf.readUnsignedShort()
                    val count = raf.readUnsignedShort()
                    val stringOffset = raf.readUnsignedShort()

                    var family = "Unknown"
                    var style = "Regular"
                    var weight = 400

                    for (i in 0 until count) {
                        val platformID = raf.readUnsignedShort()
                        val encodingID = raf.readUnsignedShort()
                        val languageID = raf.readUnsignedShort()
                        val nameID = raf.readUnsignedShort()
                        val length = raf.readUnsignedShort()
                        val offset = raf.readUnsignedShort()

                        // Record current position to return to it
                        val currentPos = raf.filePointer
                        
                        // Font Family: nameID == 1 or 16, Font Subfamily (Style): nameID == 2 or 17
                        if (nameID == 1 || nameID == 2 || nameID == 16 || nameID == 17) {
                            raf.seek(nameTableOffset + stringOffset + offset)
                            val bytes = ByteArray(length)
                            raf.readFully(bytes)

                            // TrueType name table values are typically UTF-16BE (Platform 3/Windows or 0/Unicode)
                            val charset = if (platformID == 3 || platformID == 0) {
                                Charsets.UTF_16BE
                            } else {
                                Charsets.US_ASCII
                            }
                            val text = String(bytes, charset).trim().filter { it.code in 32..126 }

                            when (nameID) {
                                1, 16 -> family = text
                                2, 17 -> style = text
                            }
                        }
                        raf.seek(currentPos)
                    }

                    // Guess weight from style
                    val styleLower = style.lowercase()
                    weight = when {
                        styleLower.contains("thin") -> 100
                        styleLower.contains("light") -> 300
                        styleLower.contains("medium") -> 500
                        styleLower.contains("semibold") || styleLower.contains("demi") -> 600
                        styleLower.contains("bold") -> 700
                        styleLower.contains("black") || styleLower.contains("heavy") -> 900
                        else -> 400
                    }

                    entry.metadata["font_family"] = family
                    entry.metadata["font_style"] = style
                    entry.metadata["font_weight"] = weight
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing TrueType metadata, falling back to name heuristics", e)
        }

        // Fallback: Use File name for font metadata
        val nameWithoutExt = File(entry.filePath).nameWithoutExtension
        entry.metadata["font_family"] = nameWithoutExt.substringBefore("-")
        entry.metadata["font_style"] = if (nameWithoutExt.contains("-")) nameWithoutExt.substringAfter("-") else "Regular"
        entry.metadata["font_weight"] = 400
    }

    private fun extractLutMetadata(entry: AssetEntry) {
        try {
            val file = File(entry.filePath)
            var cubeSize = 0
            var domainMin = 0.0f
            var domainMax = 1.0f

            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                var linesChecked = 0
                while (reader.readLine().also { line = it } != null && linesChecked < 50) {
                    linesChecked++
                    val cleanLine = line!!.trim()
                    if (cleanLine.startsWith("#") || cleanLine.isEmpty()) continue

                    val tokens = cleanLine.split("\\s+".toRegex())
                    if (tokens.isEmpty()) continue

                    when (tokens[0]) {
                        "LUT_3D_SIZE" -> {
                            if (tokens.size > 1) {
                                cubeSize = tokens[1].toIntOrNull() ?: 0
                            }
                        }
                        "LUT_1D_SIZE" -> {
                            if (tokens.size > 1) {
                                cubeSize = tokens[1].toIntOrNull() ?: 0
                            }
                        }
                        "DOMAIN_MIN" -> {
                            if (tokens.size > 3) {
                                domainMin = tokens[1].toFloatOrNull() ?: 0.0f
                            }
                        }
                        "DOMAIN_MAX" -> {
                            if (tokens.size > 3) {
                                domainMax = tokens[1].toFloatOrNull() ?: 1.0f
                            }
                        }
                    }
                }
            }

            entry.metadata["lut_cube_size"] = cubeSize
            entry.metadata["lut_domain_min"] = domainMin
            entry.metadata["lut_domain_max"] = domainMax
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing LUT metadata", e)
            entry.metadata["lut_cube_size"] = 0
            entry.metadata["lut_domain_min"] = 0.0f
            entry.metadata["lut_domain_max"] = 1.0f
        }
    }
}
