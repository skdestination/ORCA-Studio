package com.litecut.app.timeline

import android.opengl.GLES20

interface RenderTarget {
    val width: Int
    val height: Int
    fun bind()
    fun unbind()
}

class OnscreenTarget(
    override val width: Int,
    override val height: Int
) : RenderTarget {
    override fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun unbind() {
        // No-op for default onscreen back-buffer
    }
}

class OffscreenTarget(
    val frameBufferRef: FrameBufferRef
) : RenderTarget {
    override val width: Int get() = frameBufferRef.width
    override val height: Int get() = frameBufferRef.height

    override fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferRef.fboId)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun unbind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }
}
