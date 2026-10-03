package com.glowcam.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fallback for GPUs that cannot read the camera's external texture from the main (GLSL ES 3.0) shader.
 * A tiny GLSL ES 1.0 pass copies the camera frame, already rotated upright by the SurfaceTexture
 * matrix, into a normal 2D texture that the main shader can then sample.
 */
class OesPrepass {
    private val program: Int
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }

    init {
        val vs = compile(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec2 aPos;
            uniform mat4 uMst;
            varying vec2 vUv;
            void main() {
                vUv = (uMst * vec4(aPos * 0.5 + 0.5, 0.0, 1.0)).xy;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
            """.trimIndent(),
        )
        val fs = compile(
            GLES20.GL_FRAGMENT_SHADER,
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vUv;
            uniform samplerExternalOES uTex;
            void main() {
                gl_FragColor = texture2D(uTex, vUv);
            }
            """.trimIndent(),
        )
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glBindAttribLocation(program, 0, "aPos")
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Pre-pass link failed: " + GLES20.glGetProgramInfoLog(program) }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "Pre-pass compile failed: " + GLES20.glGetShaderInfoLog(s) }
        return s
    }

    /** Draws the camera frame into the currently bound framebuffer. [stMat] is the SurfaceTexture 4x4 transform. */
    fun draw(stMat: FloatArray, oesTex: Int) {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMst"), 1, false, stMat, 0)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0)
    }
}
