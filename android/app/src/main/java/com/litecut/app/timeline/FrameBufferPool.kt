package com.litecut.app.timeline

import android.opengl.GLES20
import android.util.Log
import com.litecut.app.timeline.resources.ManagedCache
import java.util.concurrent.ConcurrentHashMap

data class FrameBufferRef(
    val fboId: Int,
    val textureId: Int,
    val width: Int,
    val height: Int,
    var lastUsedTimeMs: Long = System.currentTimeMillis()
)

class FrameBufferPool private constructor() : ManagedCache {
    override val categoryName: String = "gpu_framebuffers"

    private val activeBuffers = ConcurrentHashMap<String, FrameBufferRef>()
    private val idleBuffers = ArrayList<FrameBufferRef>()

    companion object {
        @Volatile
        private var instance: FrameBufferPool? = null

        fun getInstance(): FrameBufferPool {
            return instance ?: synchronized(this) {
                instance ?: FrameBufferPool().also { instance = it }
            }
        }
    }

    /**
     * Obtains a Framebuffer of the given dimensions.
     * Reuses an idle one if available, otherwise creates a new one.
     */
    @Synchronized
    fun obtainFrameBuffer(width: Int, height: Int): FrameBufferRef {
        val idleIndex = idleBuffers.indexOfFirst { it.width == width && it.height == height }
        
        if (idleIndex >= 0) {
            val fbo = idleBuffers.removeAt(idleIndex)
            fbo.lastUsedTimeMs = System.currentTimeMillis()
            val key = "${fbo.fboId}"
            activeBuffers[key] = fbo
            return fbo
        }

        // Create new FBO
        val fboId = createFbo(width, height)
        val textureId = createFboTexture(width, height)
        
        // Bind texture to Framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            textureId,
            0
        )
        
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("FrameBufferPool", "Failed to create complete framebuffer: status $status")
        }
        
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        val ref = FrameBufferRef(fboId, textureId, width, height)
        activeBuffers["$fboId"] = ref
        return ref
    }

    @Synchronized
    fun recycleFrameBuffer(fbo: FrameBufferRef) {
        val key = "${fbo.fboId}"
        if (activeBuffers.containsKey(key)) {
            activeBuffers.remove(key)
            fbo.lastUsedTimeMs = System.currentTimeMillis()
            idleBuffers.add(fbo)
        }
    }

    private fun createFbo(width: Int, height: Int): Int {
        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        return fbos[0]
    }

    private fun createFboTexture(width: Int, height: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )
        
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textureId
    }

    override fun getCurrentSizeBytes(): Long {
        var bytes = 0L
        for (ref in activeBuffers.values) {
            bytes += ref.width * ref.height * 4L // RGBA_8888 (4 bytes per pixel)
        }
        for (ref in idleBuffers) {
            bytes += ref.width * ref.height * 4L
        }
        return bytes
    }

    @Synchronized
    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        val sortedIdle = idleBuffers.sortedBy { it.lastUsedTimeMs }
        
        for (ref in sortedIdle) {
            if (freed >= bytesToFree) break
            val size = ref.width * ref.height * 4L
            
            val fbos = intArrayOf(ref.fboId)
            val textures = intArrayOf(ref.textureId)
            GLES20.glDeleteFramebuffers(1, fbos, 0)
            GLES20.glDeleteTextures(1, textures, 0)
            
            idleBuffers.remove(ref)
            freed += size
        }
        Log.d("FrameBufferPool", "Trimmed memory: Freed $freed bytes of GPU framebuffers")
    }

    @Synchronized
    override fun clear() {
        val fbosToDelete = ArrayList<Int>()
        val texturesToDelete = ArrayList<Int>()

        for (ref in activeBuffers.values) {
            fbosToDelete.add(ref.fboId)
            texturesToDelete.add(ref.textureId)
        }
        for (ref in idleBuffers) {
            fbosToDelete.add(ref.fboId)
            texturesToDelete.add(ref.textureId)
        }

        if (fbosToDelete.isNotEmpty()) {
            GLES20.glDeleteFramebuffers(fbosToDelete.size, fbosToDelete.toIntArray(), 0)
            GLES20.glDeleteTextures(texturesToDelete.size, texturesToDelete.toIntArray(), 0)
        }

        activeBuffers.clear()
        idleBuffers.clear()
        Log.i("FrameBufferPool", "Cleared all GPU framebuffers")
    }
}
