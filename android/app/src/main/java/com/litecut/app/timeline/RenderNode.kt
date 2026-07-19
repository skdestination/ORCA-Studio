package com.litecut.app.timeline

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

enum class RenderNodeType {
    CLEAR,
    VIDEO_DECODER_FRAME,
    IMAGE_TEXTURE,
    TEXT_GRAVITY,
    COLOR_CORRECTION,
    TRANSITION_BLENDER,
    BLUR_FILTER,
    FINAL_PRESENT
}

open class RenderNode(
    val nodeId: String,
    val nodeType: RenderNodeType
) {
    // Shared geometry buffers to prevent runtime allocations or JVM GC triggers
    companion object {
        private val squareCoords = floatArrayOf(
            -1.0f,  1.0f, 0.0f, // Top left
            -1.0f, -1.0f, 0.0f, // Bottom left
             1.0f, -1.0f, 0.0f, // Bottom right
             1.0f,  1.0f, 0.0f  // Top right
        )

        private val textureCoords = floatArrayOf(
            0.0f, 0.0f, // Top left
            0.0f, 1.0f, // Bottom left
            1.0f, 1.0f, // Bottom right
            1.0f, 0.0f  // Top right
        )

        val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(squareCoords)
                position(0)
            }
        }

        val textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(textureCoords)
                position(0)
            }
        }
    }

    // Node-specific rendering configurations
    var opacity: Float = 1.0f
    var translationX: Float = 0.0f
    var translationY: Float = 0.0f
    var scaleX: Float = 1.0f
    var scaleY: Float = 1.0f
    var rotation: Float = 0.0f

    // Cropping rect in coordinates [0.0 - 1.0] (Left, Top, Right, Bottom)
    var cropLeft: Float = 0.0f
    var cropTop: Float = 0.0f
    var cropRight: Float = 0.0f
    var cropBottom: Float = 0.0f

    // Associated GPU Textures or FBOs
    var inputTextureId: Int = 0
    var secondaryTextureId: Int = 0 // For transitions
    var progress: Float = 0.0f       // For transitions

    // Output target
    var target: RenderTarget? = null

    /**
     * Executes GLES20 drawings. Uses ShaderManager and sets all uniforms safely.
     */
    open fun draw(context: RenderContext, shaderManager: ShaderManager, stats: RenderStatistics) {
        val targetLocal = target ?: return
        targetLocal.bind()

        if (nodeType == RenderNodeType.CLEAR) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            targetLocal.unbind()
            return
        }

        // Get shader program
        val program = when (nodeType) {
            RenderNodeType.TRANSITION_BLENDER -> {
                shaderManager.getOrCreateProgram(
                    "TransitionBlender",
                    shaderManager.standardVertexShader,
                    shaderManager.transitionFragmentShader
                )
            }
            else -> {
                shaderManager.getOrCreateProgram(
                    "StandardShader",
                    shaderManager.standardVertexShader,
                    shaderManager.standardFragmentShader
                )
            }
        }

        program.use()

        // Bind attributes
        val posHandle = GLES20.glGetAttribLocation(program.programId, "a_Position")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        val texHandle = GLES20.glGetAttribLocation(program.programId, "a_TexCoord")
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)

        // Set MVP transform matrix
        context.calculateMvp(translationX, translationY, scaleX, scaleY, rotation)
        val mvpMatrixHandle = GLES20.glGetUniformLocation(program.programId, "u_MVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, context.mvpMatrix, 0)

        // Set standard attributes
        val opacityHandle = GLES20.glGetUniformLocation(program.programId, "u_Opacity")
        GLES20.glUniform1f(opacityHandle, opacity)

        val cropHandle = GLES20.glGetUniformLocation(program.programId, "u_CropRect")
        if (cropHandle >= 0) {
            GLES20.glUniform4f(cropHandle, cropLeft, cropTop, cropRight, cropBottom)
        }

        // Bind textures
        if (nodeType == RenderNodeType.TRANSITION_BLENDER) {
            val outTexHandle = GLES20.glGetUniformLocation(program.programId, "u_OutgoingTexture")
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
            GLES20.glUniform1i(outTexHandle, 0)

            val inTexHandle = GLES20.glGetUniformLocation(program.programId, "u_IncomingTexture")
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, secondaryTextureId)
            GLES20.glUniform1i(inTexHandle, 1)

            val progressHandle = GLES20.glGetUniformLocation(program.programId, "u_Progress")
            GLES20.glUniform1f(progressHandle, progress)
        } else {
            val texSamplerHandle = GLES20.glGetUniformLocation(program.programId, "u_Texture")
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
            GLES20.glUniform1i(texSamplerHandle, 0)
        }

        // Draw arrays representing the 2D quad surface
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)

        stats.recordDrawCall()
        targetLocal.unbind()
    }
}
