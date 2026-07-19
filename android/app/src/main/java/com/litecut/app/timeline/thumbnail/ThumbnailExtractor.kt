package com.litecut.app.timeline.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.math.max

class ThumbnailExtractor(private val context: Context, private val bitmapPool: BitmapPool) {

    /**
     * Extracts a scaled thumbnail from a video or image clip.
     */
    fun extract(src: String, isVideo: Boolean, timeOffsetSeconds: Double, targetWidth: Int, targetHeight: Int): Bitmap? {
        return if (isVideo) {
            extractVideoFrame(src, timeOffsetSeconds, targetWidth, targetHeight)
        } else {
            extractImageFrame(src, targetWidth, targetHeight)
        }
    }

    private fun extractVideoFrame(src: String, timeOffsetSeconds: Double, targetWidth: Int, targetHeight: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(src)
            if (src.startsWith("content://") || src.startsWith("android.resource://") || uri.scheme != null) {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(src)
            }

            // Convert seconds to microseconds
            val timeUs = (timeOffsetSeconds * 1_000_000.0).toLong()

            // Read video rotation
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // Query scaled frame if API is 27 or higher (very memory-efficient!)
            var rawBitmap: Bitmap? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    // Adjust dimensions if rotated 90 or 270 degrees
                    val (reqW, reqH) = if (rotation == 90 || rotation == 270) {
                        Pair(targetHeight, targetWidth)
                    } else {
                        Pair(targetWidth, targetHeight)
                    }
                    rawBitmap = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, reqW, reqH)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // Fallback for older devices or if getScaledFrameAtTime failed
            if (rawBitmap == null) {
                rawBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }

            if (rawBitmap == null) return null

            // Scale and rotate rawBitmap to exactly match requested thumbnail dimension and rotation
            val finalBitmap = processRawBitmap(rawBitmap, rotation, targetWidth, targetHeight)
            
            // If processRawBitmap produced a new bitmap, recycle or pool the rawBitmap
            if (finalBitmap != rawBitmap) {
                bitmapPool.put(rawBitmap)
            }
            return finalBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    private fun extractImageFrame(src: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            val uri = Uri.parse(src)
            val isContentUri = src.startsWith("content://") || uri.scheme != null

            // Get original dimensions
            if (isContentUri) {
                context.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } else {
                BitmapFactory.decodeFile(src, options)
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, targetWidth, targetHeight)
            options.inJustDecodeBounds = false
            options.inMutable = true

            // Try to reuse an existing bitmap from pool
            val recycledBitmap = bitmapPool.get(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            if (recycledBitmap != null && !recycledBitmap.isRecycled) {
                options.inBitmap = recycledBitmap
            }

            val decoded: Bitmap? = if (isContentUri) {
                context.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } else {
                BitmapFactory.decodeFile(src, options)
            }

            if (decoded == null) return null

            val finalBitmap = processRawBitmap(decoded, 0, targetWidth, targetHeight)
            if (finalBitmap != decoded) {
                bitmapPool.put(decoded)
            }
            return finalBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun processRawBitmap(raw: Bitmap, rotation: Int, dstWidth: Int, dstHeight: Int): Bitmap {
        var width = raw.width
        var height = raw.height
        
        val matrix = Matrix()
        
        // Handle video rotation
        if (rotation != 0) {
            matrix.postRotate(rotation.toFloat())
        }

        // Handle scaling to destination size exactly
        val scaleX = dstWidth.toFloat() / width
        val scaleY = dstHeight.toFloat() / height
        val scale = max(scaleX, scaleY) // Center crop scale
        matrix.postScale(scale, scale)

        // Try reusing bitmap from BitmapPool for output
        val pooled = bitmapPool.get(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
        val outBitmap = if (pooled != null && !pooled.isRecycled) {
            pooled
        } else {
            Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
        }

        val canvas = android.graphics.Canvas(outBitmap)
        
        // Center crop matrix drawing
        val dx = (dstWidth - width * scale) / 2f
        val dy = (dstHeight - height * scale) / 2f
        
        val drawMatrix = Matrix()
        if (rotation != 0) {
            // Apply rotation and offset around center
            drawMatrix.postTranslate(-width / 2f, -height / 2f)
            drawMatrix.postRotate(rotation.toFloat())
            // Swap width/height coordinates if rotated 90 or 270
            if (rotation == 90 || rotation == 270) {
                drawMatrix.postTranslate(height / 2f, width / 2f)
                drawMatrix.postScale(dstWidth.toFloat() / height, dstHeight.toFloat() / width)
            } else {
                drawMatrix.postTranslate(width / 2f, height / 2f)
                drawMatrix.postScale(scaleX, scaleY)
            }
        } else {
            drawMatrix.postScale(scale, scale)
            drawMatrix.postTranslate(dx, dy)
        }

        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(raw, drawMatrix, paint)
        return outBitmap
    }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (srcH > reqH || srcW > reqW) {
            val halfHeight = srcH / 2
            val halfWidth = srcW / 2
            while (halfHeight / inSampleSize >= reqH && halfWidth / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
