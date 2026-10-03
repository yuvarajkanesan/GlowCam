package com.glowcam.gl

import android.opengl.GLES20
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Compiles the effect shader and draws one full-screen quad with all uniforms set. */
class EffectProgram(oes: Boolean) {
    private val program: Int
    private val locs = HashMap<String, Int>()
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }
    private val faceU = FaceUniforms()
    private val blem = FloatArray(16 * 4)

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, Shaders.VERTEX)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, Shaders.fragment(oes))
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glBindAttribLocation(program, 0, "aPos")
        GLES30.glLinkProgram(program)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Link failed: " + GLES30.glGetProgramInfoLog(program) }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "Shader compile failed: " + GLES30.glGetShaderInfoLog(s) }
        return s
    }

    private fun loc(name: String) = locs.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    private fun f1(n: String, v: Float) = GLES30.glUniform1f(loc(n), v)
    private fun i1(n: String, v: Int) = GLES30.glUniform1i(loc(n), v)

    /**
     * Draws into the currently bound framebuffer / surface of size [outW]x[outH].
     * [srcMat] maps upright source uv -> texture coords; [aspect] is the upright source w/h.
     * [crop] is the visible region of the source (also used for cover-fit on screen).
     */
    fun draw(
        srcMat: FloatArray,
        aspect: Float,
        outW: Int,
        outH: Int,
        p: EffectParams,
        crop: CropRect,
        faces: List<com.glowcam.face.FaceLandmarks>,
        flipOut: Boolean,
        textureTarget: Int,
        textureId: Int,
        maskTex: Int = 0,
    ) {
        GLES30.glUseProgram(program)
        // person mask on texture unit 1 (0 = none; the shader skips subject effects then)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTex)
        i1("uMask", 1)
        i1("uHasMask", if (maskTex != 0) 1 else 0)
        i1("uMode", p.filter.mode)
        f1("uBgAmt", p.filter.bgAmount)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(textureTarget, textureId)
        i1("uTex", 0)

        GLES30.glUniformMatrix3fv(loc("uSrc"), 1, false, Mat3.toGl(srcMat), 0)
        f1("uAspect", aspect)
        f1("uOutAspect", outW.toFloat() / outH)
        f1("uFlipOut", if (flipOut) -1f else 1f)
        GLES30.glUniform4f(loc("uCrop"), crop.x, crop.y, crop.w, crop.h)

        val rad = Math.toRadians(p.straightenDeg.toDouble()).toFloat()
        f1("uStraighten", rad)

        // faces
        FaceGeometry.fill(faceU, faces, p, aspect)
        i1("uFaceN", faceU.faceN); GLES30.glUniform4fv(loc("uFace"), 3, faceU.face, 0)
        i1("uMouthN", faceU.mouthN); GLES30.glUniform4fv(loc("uMouth"), 3, faceU.mouth, 0)
        i1("uPushN", faceU.pushN)
        GLES30.glUniform4fv(loc("uPush"), 32, faceU.push, 0)
        GLES30.glUniform2fv(loc("uPushDir"), 32, faceU.pushDir, 0)
        i1("uEyeN", faceU.eyeN); GLES30.glUniform4fv(loc("uEye"), 6, faceU.eye, 0)
        i1("uTeethN", faceU.teethN); GLES30.glUniform4fv(loc("uTeethE"), 3, faceU.teeth, 0)
        i1("uBagN", faceU.bagN); GLES30.glUniform4fv(loc("uBag"), 6, faceU.bag, 0)
        i1("uRedN", faceU.redN); GLES30.glUniform4fv(loc("uRed"), 6, faceU.red, 0)

        val bl = p.blemishes.take(16)
        for ((i, b) in bl.withIndex()) {
            blem[i * 4] = b.x; blem[i * 4 + 1] = b.y; blem[i * 4 + 2] = b.r; blem[i * 4 + 3] = 0f
        }
        i1("uBlemN", bl.size)
        GLES30.glUniform4fv(loc("uBlem"), 16, blem, 0)

        // background replace
        i1("uBgMode", p.bgMode)
        GLES30.glUniform3f(loc("uBgC1"), rgb(p.bgColor1, 16), rgb(p.bgColor1, 8), rgb(p.bgColor1, 0))
        GLES30.glUniform3f(loc("uBgC2"), rgb(p.bgColor2, 16), rgb(p.bgColor2, 8), rgb(p.bgColor2, 0))
        f1("uBgBlur", p.bgBlur)

        // makeup
        i1("uLipN", faceU.lipN)
        GLES30.glUniform2fv(loc("uLipOuter"), 60, faceU.lipOuter, 0)
        GLES30.glUniform2fv(loc("uLipInner"), 60, faceU.lipInner, 0)
        GLES30.glUniform4fv(loc("uLipBox"), 3, faceU.lipBox, 0)
        GLES30.glUniform4f(loc("uLipCol"), rgb(p.lipColor, 16), rgb(p.lipColor, 8), rgb(p.lipColor, 0), if (faceU.lipN > 0) p.lip else 0f)
        i1("uBlushN", faceU.blushN)
        GLES30.glUniform4fv(loc("uBlush"), 6, faceU.blush, 0)
        GLES30.glUniform4f(loc("uBlushCol"), rgb(p.blushColor, 16), rgb(p.blushColor, 8), rgb(p.blushColor, 0), if (faceU.blushN > 0) p.blush else 0f)
        i1("uBrowN", faceU.browN)
        GLES30.glUniform2fv(loc("uBrow"), 30, faceU.brow, 0)
        GLES30.glUniform1fv(loc("uBrowW"), 3, faceU.browW, 0)
        GLES30.glUniform4f(loc("uBrowCol"), rgb(p.browColor, 16), rgb(p.browColor, 8), rgb(p.browColor, 0), if (faceU.browN > 0) p.brow else 0f)
        i1("uShadeN", faceU.shadeN)
        GLES30.glUniform4fv(loc("uShade"), 6, faceU.shade, 0)
        GLES30.glUniform4f(loc("uShadeCol"), rgb(p.shadowColor, 16), rgb(p.shadowColor, 8), rgb(p.shadowColor, 0), if (faceU.shadeN > 0) p.eyeShadow else 0f)

        f1("uSmooth", p.smooth)
        f1("uSmoothR", faceU.smoothR)
        f1("uBright", p.brighten)
        f1("uTeethAmt", p.teeth)
        f1("uBagAmt", p.darkCircles)
        f1("uRedAmt", p.redEye)

        // filter
        val f = p.filter
        GLES30.glUniform4f(loc("uFA"), f.exposure, f.contrast, f.saturation, f.temp)
        GLES30.glUniform4f(loc("uFB"), f.tint, f.fade, f.vignette, 0f)
        GLES30.glUniform3f(loc("uShadow"), rgb(f.shadow, 16), rgb(f.shadow, 8), rgb(f.shadow, 0))
        GLES30.glUniform3f(loc("uHigh"), rgb(f.highlight, 16), rgb(f.highlight, 8), rgb(f.highlight, 0))
        f1("uBW", f.bw)
        f1("uGlow", f.glow)
        f1("uFMix", if (f === Filters.ORIGINAL || f.id == "original") 0f else p.filterIntensity)

        // adjust
        f1("uBrightness", p.brightness)
        f1("uContrast", p.contrast)
        f1("uSaturation", p.saturation)
        f1("uBlur", p.blur)
        i1("uRadialBlur", if (p.radialBlur) 1 else 0)
        f1("uVig", p.vignette)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun rgb(c: Int, shift: Int) = ((c shr shift) and 0xFF) / 255f

    fun release() = GLES20.glDeleteProgram(program)

    companion object {
        /** Crop that makes a source of aspect [srcAspect] cover an output of aspect [outAspect]. */
        fun coverCrop(srcAspect: Float, outAspect: Float): CropRect =
            if (outAspect > srcAspect) {
                val h = srcAspect / outAspect
                CropRect(0f, (1f - h) / 2f, 1f, h)
            } else {
                val w = outAspect / srcAspect
                CropRect((1f - w) / 2f, 0f, w, 1f)
            }
    }
}
