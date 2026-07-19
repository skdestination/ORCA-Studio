package com.litecut.app.export;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class TextureRender {
    private static final String VERTEX_SHADER = 
        "attribute vec4 aPosition;\n" +
        "attribute vec4 aTexCoord;\n" +
        "varying vec2 vTexCoord;\n" +
        "uniform mat4 uSTMatrix;\n" +
        "void main() {\n" +
        "  gl_Position = aPosition;\n" +
        "  vTexCoord = (uSTMatrix * aTexCoord).xy;\n" +
        "}";

    private static final String FRAGMENT_SHADER = 
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +
        "varying vec2 vTexCoord;\n" +
        "uniform samplerExternalOES sTexture;\n" +
        "void main() {\n" +
        "  gl_FragColor = texture2D(sTexture, vTexCoord);\n" +
        "}";

    private final FloatBuffer mTriangleVertices;
    private static final float[] mTriangleVerticesData = {
        -1.0f, -1.0f, 0, 0.f, 0.f,
         1.0f, -1.0f, 0, 1.f, 0.f,
        -1.0f,  1.0f, 0, 0.f, 1.f,
         1.0f,  1.0f, 0, 1.f, 1.f,
    };

    private int mProgram;
    private int maPositionLoc;
    private int maTexCoordLoc;
    private int muSTMatrixLoc;

    public TextureRender() {
        mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVertices.put(mTriangleVerticesData).position(0);
    }

    public void surfaceCreated() {
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgram == 0) throw new RuntimeException("failed creating program");
        
        maPositionLoc = GLES20.glGetAttribLocation(mProgram, "aPosition");
        maTexCoordLoc = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        muSTMatrixLoc = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        checkGlError("glGetAttribLocation/UniformLocation");
    }

    public void drawFrame(int textureId, float[] stMatrix) {
        GLES20.glUseProgram(mProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniformMatrix4fv(muSTMatrixLoc, 1, false, stMatrix, 0);

        mTriangleVertices.position(0);
        GLES20.glVertexAttribPointer(maPositionLoc, 3, GLES20.GL_FLOAT, false, 20, mTriangleVertices);
        GLES20.glEnableVertexAttribArray(maPositionLoc);

        mTriangleVertices.position(3);
        GLES20.glVertexAttribPointer(maTexCoordLoc, 2, GLES20.GL_FLOAT, false, 20, mTriangleVertices);
        GLES20.glEnableVertexAttribArray(maTexCoordLoc);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return 0;
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) return 0;
        int program = GLES20.glCreateProgram();
        if (program == 0) return 0;
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("Could not link program");
        }
        return program;
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) return 0;
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Could not compile shader " + shaderType);
        }
        return shader;
    }

    private void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}
