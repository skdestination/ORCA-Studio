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

        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        maPositionLoc = GLES20.glGetAttribLocation(mProgram, "aPosition");
        maTexCoordLoc = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        muSTMatrixLoc = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
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
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        return program;
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
