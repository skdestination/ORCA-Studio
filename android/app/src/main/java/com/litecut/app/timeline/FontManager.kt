package com.litecut.app.timeline

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import java.io.File

class FontManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: FontManager? = null

        fun getInstance(context: Context? = null): FontManager {
            return instance ?: synchronized(this) {
                instance ?: if (context != null) {
                    FontManager(context.applicationContext).also { instance = it }
                } else {
                    throw IllegalStateException("FontManager is not initialized. Please pass a valid Context first.")
                }
            }
        }
    }

    private val fontCache = FontCache.getInstance(context)

    init {
        // Pre-warm Cache with system default typefaces
        fontCache.putTypeface("sans-serif", Typeface.SANS_SERIF)
        fontCache.putTypeface("serif", Typeface.SERIF)
        fontCache.putTypeface("monospace", Typeface.MONOSPACE)
        fontCache.putTypeface("default", Typeface.DEFAULT)
        fontCache.putTypeface("default-bold", Typeface.DEFAULT_BOLD)
    }

    /**
     * Retrieves typeface synchronously from cache, or falls back.
     */
    fun resolveFont(familyName: String, weight: String = "normal", style: String = "normal"): Typeface {
        val cacheKey = buildCacheKey(familyName, weight, style)
        val cached = fontCache.getTypeface(cacheKey)
        if (cached != null) return cached

        // Fallback checks
        val familyFallback = fontCache.getTypeface(familyName)
        if (familyFallback != null) return familyFallback

        // Map basic weight/styles to Typeface style flags
        val styleFlag = getStyleFlag(weight, style)
        return try {
            val systemTypeface = Typeface.create(familyName, styleFlag)
            if (systemTypeface != Typeface.DEFAULT) {
                fontCache.putTypeface(cacheKey, systemTypeface)
                systemTypeface
            } else {
                Typeface.defaultFromStyle(styleFlag)
            }
        } catch (e: Exception) {
            Log.e("FontManager", "Failed to resolve system font: $familyName, falling back.", e)
            Typeface.defaultFromStyle(styleFlag)
        }
    }

    /**
     * Asynchronously loads custom local or network downloaded fonts into memory using TaskScheduler.
     */
    fun loadCustomFontAsync(fontFilePath: String, familyName: String, weight: String = "normal", style: String = "normal", onComplete: (Boolean) -> Unit) {
        val cacheKey = buildCacheKey(familyName, weight, style)
        if (fontCache.getTypeface(cacheKey) != null) {
            onComplete(true)
            return
        }

        TaskScheduler.getInstance(context).submit(
            name = "LoadCustomFont-$familyName",
            priority = TaskPriority.HIGH
        ) { token, progress ->
            try {
                val file = File(fontFilePath)
                if (file.exists()) {
                    val customTypeface = Typeface.createFromFile(file)
                    fontCache.putTypeface(cacheKey, customTypeface)
                    Log.i("FontManager", "Successfully loaded custom font into memory cache: $cacheKey")
                    onComplete(true)
                    true
                } else {
                    Log.e("FontManager", "Custom font file not found: $fontFilePath")
                    onComplete(false)
                    false
                }
            } catch (e: Exception) {
                Log.e("FontManager", "Error parsing custom font file: $fontFilePath", e)
                onComplete(false)
                false
            }
        }
    }

    private fun buildCacheKey(family: String, weight: String, style: String): String {
        return "${family.lowercase()}_${weight.lowercase()}_${style.lowercase()}"
    }

    private fun getStyleFlag(weight: String, style: String): Int {
        val isBold = weight.lowercase() == "bold" || weight.lowercase() == "black" || weight.toIntOrNull() ?: 400 >= 700
        val isItalic = style.lowercase() == "italic" || style.lowercase() == "oblique"

        return when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
    }
}
