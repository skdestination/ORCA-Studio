package com.litecut.app.timeline

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap

data class TextureRef(
    val textureId: Int,
    val width: Int,
    val height: Int,
    val config: Bitmap.Config,
    var lastUsedTimeMs: Long = System.currentTimeMillis()
)

class TextureManager private constructor() : ManagedCache {
    override val categoryName: String = "gpu_textures"
    
    // Maps a resource key (e.g. clipId or asset path) to a texture reference
    private val activeTextures = ConcurrentHashMap<String, TextureRef>()
    // Pool of recycled textures to avoid reallocation
    private val texturePool = ArrayList<TextureRef>()
    
    private val stats = RenderStatistics()

    companion object {
        @Volatile
        private var instance: TextureManager? = null

        fun getInstance(): TextureManager {
            return instance ?: synchronized(this) {
                instance ?: TextureManager().also { instance = it }
            }
        }
    }

    /**
     * Obtains or uploads a texture from a Bitmap.
     * Implements zero recreation of textures.
     */
    @Synchronized
    fun getOrCreateTexture(key: String, bitmap: Bitmap): Int {
        val existing = activeTextures[key]
        if (existing != null) {
            existing.lastUsedTimeMs = System.currentTimeMillis()
            stats.recordTextureHit()
            return existing.textureId
        }

        stats.recordTextureMiss()
        
        // Try to obtain a recycled texture of matching dimensions and config
        val recycledIndex = texturePool.indexOfFirst { 
            it.width == bitmap.width && it.height == bitmap.height && it.config == bitmap.config 
        }

        val textureId: Int
        if (recycledIndex >= 0) {
            val recycled = texturePool.removeAt(recycledIndex)
            textureId = recycled.textureId
            updateTextureWithBitmap(textureId, bitmap)
            recycled.lastUsedTimeMs = System.currentTimeMillis()
            activeTextures[key] = recycled
        } else {
            textureId = generateAndUploadTexture(bitmap)
            if (textureId > 0) {
                val ref = TextureRef(textureId, bitmap.width, bitmap.height, bitmap.config)
                activeTextures[key] = ref
            }
        }

        return textureId
    }

    private fun generateAndUploadTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        
        if (textureId == 0) {
            Log.e("TextureManager", "Failed to generate OpenGL texture ID")
            return 0
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        
        // Set standard high-fidelity wrapping and filtering parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        
        return textureId
    }

    private fun updateTextureWithBitmap(textureId: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    @Synchronized
    fun recycleTexture(key: String) {
        val ref = activeTextures.remove(key)
        if (ref != null) {
            texturePool.add(ref)
        }
    }

    override fun getCurrentSizeBytes(): Long {
        var bytes = 0L
        for (ref in activeTextures.values) {
            bytes += ref.width * ref.height * getBytesPerPixel(ref.config)
        }
        for (ref in texturePool) {
            bytes += ref.width * ref.height * getBytesPerPixel(ref.config)
        }
        return bytes
    }

    private fun getBytesPerPixel(config: Bitmap.Config): Int {
        return when (config) {
            Bitmap.Config.ARGB_8888 -> 4
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ALPHA_8 -> 1
            else -> 4
        }
    }

    @Synchronized
    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        // First, evict from recycled pool (oldest first)
        val sortedPool = texturePool.sortedBy { it.lastUsedTimeMs }
        for (ref in sortedPool) {
            if (freed >= bytesToFree) break
            val size = ref.width * ref.height * getBytesPerPixel(ref.config)
            
            val textures = intArrayOf(ref.textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            texturePool.remove(ref)
            freed += size
        }

        // If still need to free, evict from active textures (not currently locked or recently used)
        if (freed < bytesToFree) {
            val sortedActiveKeys = activeTextures.entries
                .sortedBy { it.value.lastUsedTimeMs }
                .map { it.key }
            
            for (key in sortedActiveKeys) {
                if (freed >= bytesToFree) break
                val ref = activeTextures[key] ?: continue
                val size = ref.width * ref.height * getBytesPerPixel(ref.config)
                
                val textures = intArrayOf(ref.textureId)
                GLES20.glDeleteTextures(1, textures, 0)
                activeTextures.remove(key)
                freed += size
            }
        }
        
        Log.d("TextureManager", "Trimmed memory: Freed $freed bytes of GPU textures")
    }

    @Synchronized
    override fun clear() {
        val texturesToDelete = ArrayList<Int>()
        for (ref in activeTextures.values) {
            texturesToDelete.add(ref.textureId)
        }
        for (ref in texturePool) {
            texturesToDelete.add(ref.textureId)
        }

        if (texturesToDelete.isNotEmpty()) {
            val arr = texturesToDelete.toIntArray()
            GLES20.glDeleteTextures(arr.size, arr, 0)
        }

        activeTextures.clear()
        texturePool.clear()
        Log.i("TextureManager", "Cleared all GPU textures")
    }
}
