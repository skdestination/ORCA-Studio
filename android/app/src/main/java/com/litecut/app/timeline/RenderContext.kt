package com.litecut.app.timeline

import android.opengl.Matrix

class RenderContext(
    var viewportWidth: Int = 1920,
    var viewportHeight: Int = 1080,
    var currentTimeSeconds: Double = 0.0,
    var isProxyMode: Boolean = false
) {
    // Zero-allocation pre-allocated arrays for matrix operations
    val modelMatrix = FloatArray(16)
    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)
    val mvpMatrix = FloatArray(16)
    
    // Auxiliary matrix for nested transformations
    private val tempMatrix = FloatArray(16)

    init {
        resetMatrices()
    }

    fun resetMatrices() {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(mvpMatrix, 0)
        
        // Setup default camera viewing matrix
        Matrix.setLookAtM(
            viewMatrix, 0,
            0.0f, 0.0f, 3.0f,  // Eye XYZ
            0.0f, 0.0f, 0.0f,  // Center XYZ
            0.0f, 1.0f, 0.0f   // Up vector XYZ
        )

        // Setup standard 2D ortho projection
        Matrix.orthoM(
            projectionMatrix, 0,
            -1.0f, 1.0f,
            -1.0f, 1.0f,
            1.0f, 10.0f
        )
    }

    /**
     * Calculates combined MVP matrix based on translation, scale, rotation and projection.
     * Zero allocations.
     */
    fun calculateMvp(
        translationX: Float,
        translationY: Float,
        scaleX: Float,
        scaleY: Float,
        rotationDegrees: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        
        // Apply Translation
        Matrix.translateM(modelMatrix, 0, translationX, translationY, 0f)
        
        // Apply Rotation
        if (rotationDegrees != 0f) {
            Matrix.rotateM(modelMatrix, 0, rotationDegrees, 0f, 0f, 1f)
        }
        
        // Apply Scale
        Matrix.scaleM(modelMatrix, 0, scaleX, scaleY, 1f)

        // Combine Model and View matrices
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        
        // Combine MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    }
}
