package com.litecut.app.timeline

import android.opengl.GLES20
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class ShaderProgram(
    val programId: Int,
    val vertexShaderId: Int,
    val fragmentShaderId: Int
) {
    fun use() {
        GLES20.glUseProgram(programId)
    }
}

class ShaderManager private constructor() {
    private val compiledPrograms = ConcurrentHashMap<String, ShaderProgram>()

    companion object {
        @Volatile
        private var instance: ShaderManager? = null

        fun getInstance(): ShaderManager {
            return instance ?: synchronized(this) {
                instance ?: ShaderManager().also { instance = it }
            }
        }
    }

    /**
     * Standard vertex shader used for standard screen rendering, ortho-projection, cropping, and transforms.
     */
    val standardVertexShader = """
        attribute vec4 a_Position;
        attribute vec2 a_TexCoord;
        varying vec2 v_TexCoord;
        uniform mat4 u_MVPMatrix;
        void main() {
            gl_Position = u_MVPMatrix * a_Position;
            v_TexCoord = a_TexCoord;
        }
    """.trimIndent()

    /**
     * Standard fragment shader supporting video sampling, opacity control, and basic color correction/tint.
     */
    val standardFragmentShader = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_Texture;
        uniform float u_Opacity;
        uniform vec4 u_CropRect; // Left, Top, Right, Bottom [0.0 - 1.0]
        void main() {
            // Check cropping bounds
            if (v_TexCoord.x < u_CropRect.x || v_TexCoord.x > (1.0 - u_CropRect.z) ||
                v_TexCoord.y < u_CropRect.y || v_TexCoord.y > (1.0 - u_CropRect.w)) {
                discard;
            }
            vec4 color = texture2D(u_Texture, v_TexCoord);
            gl_FragColor = color * u_Opacity;
        }
    """.trimIndent()

    /**
     * Fragment shader specifically designed for transitions like cross dissolve and blending incoming/outgoing textures.
     */
    val transitionFragmentShader = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_OutgoingTexture;
        uniform sampler2D u_IncomingTexture;
        uniform float u_Progress;
        uniform int u_TransitionType; // 0=CrossDissolve, 1=Wipe, 2=Slide, etc.
        void main() {
            vec4 outColor = texture2D(u_OutgoingTexture, v_TexCoord);
            vec4 inColor = texture2D(u_IncomingTexture, v_TexCoord);
            
            if (u_TransitionType == 0) { // CROSS_DISSOLVE
                gl_FragColor = mix(outColor, inColor, u_Progress);
            } else if (u_TransitionType == 1) { // WIPE
                if (v_TexCoord.x < u_Progress) {
                    gl_FragColor = inColor;
                } else {
                    gl_FragColor = outColor;
                }
            } else {
                gl_FragColor = mix(outColor, inColor, u_Progress);
            }
        }
    """.trimIndent()

    /**
     * Obtains or compiles an OpenGL ES shader program.
     */
    @Synchronized
    fun getOrCreateProgram(key: String, vertexSource: String, fragmentSource: String): ShaderProgram {
        val existing = compiledPrograms[key]
        if (existing != null && GLES20.glIsProgram(existing.programId)) {
            return existing
        }

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        
        val programId = GLES20.glCreateProgram()
        if (programId == 0) {
            Log.e("ShaderManager", "Failed to create OpenGL ES program")
            return ShaderProgram(0, vertexShader, fragmentShader)
        }

        GLES20.glAttachShader(programId, vertexShader)
        GLES20.glAttachShader(programId, fragmentShader)
        GLES20.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(programId)
            Log.e("ShaderManager", "Error linking program '$key': $log")
            GLES20.glDeleteProgram(programId)
            return ShaderProgram(0, vertexShader, fragmentShader)
        }

        val program = ShaderProgram(programId, vertexShader, fragmentShader)
        compiledPrograms[key] = program
        Log.d("ShaderManager", "Successfully compiled and linked shader program: $key")
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shaderId = GLES20.glCreateShader(type)
        if (shaderId == 0) {
            Log.e("ShaderManager", "Failed to create shader of type $type")
            return 0
        }

        GLES20.glShaderSource(shaderId, source)
        GLES20.glCompileShader(shaderId)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shaderId)
            Log.e("ShaderManager", "Error compiling shader: $log")
            GLES20.glDeleteShader(shaderId)
            return 0
        }

        return shaderId
    }

    /**
     * Clears cache and compiles programs again upon context loss.
     */
    @Synchronized
    fun handleContextLoss() {
        Log.w("ShaderManager", "OpenGL context lost. Invalidating shader cache.")
        compiledPrograms.clear()
    }

    @Synchronized
    fun release() {
        for (prog in compiledPrograms.values) {
            GLES20.glDetachShader(prog.programId, prog.vertexShaderId)
            GLES20.glDetachShader(prog.programId, prog.fragmentShaderId)
            GLES20.glDeleteShader(prog.vertexShaderId)
            GLES20.glDeleteShader(prog.fragmentShaderId)
            GLES20.glDeleteProgram(prog.programId)
        }
        compiledPrograms.clear()
        Log.i("ShaderManager", "Released all compiled shaders")
    }
}
