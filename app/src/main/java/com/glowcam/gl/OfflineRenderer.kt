package com.glowcam.gl

import android.graphics.Bitmap
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import com.glowcam.face.FaceLandmarks
import com.glowcam.face.SubjectMask
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a Bitmap through the same shader as the live preview. Used for full-resolution photo
 * capture, the editor preview and export, so what you see live is what gets saved.
 */
object OfflineRenderer {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "glow-offline") }.asCoroutineDispatcher()

    private var egl: EglCore? = null
    private var pbuffer: EGLSurface? = null
    private var program: EffectProgram? = null
    private var cachedBitmap: Bitmap? = null
    private var cachedTex = 0
    private var maxTex = 4096

    private fun ensureGl() {
        if (egl != null) return
        val e = EglCore(recordable = false)
        egl = e
        pbuffer = e.createPbuffer(1, 1).also { e.makeCurrent(it) }
        program = EffectProgram(oes = false)
        val v = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, v, 0)
        maxTex = v[0].coerceAtLeast(2048)
    }

    private fun textureFor(bmp: Bitmap): Int {
        if (cachedBitmap === bmp && cachedTex != 0) return cachedTex
        if (cachedTex != 0) GLES30.glDeleteTextures(1, intArrayOf(cachedTex), 0)
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        cachedBitmap = bmp
        cachedTex = t[0]
        return t[0]
    }

    /** Output size for [src] after crop, scaled so the longest side is at most [maxSide]. */
    fun outputSize(srcW: Int, srcH: Int, crop: CropRect, maxSide: Int): Pair<Int, Int> {
        val w = max(2f, crop.w * srcW)
        val h = max(2f, crop.h * srcH)
        val scale = min(1f, maxSide.toFloat() / max(w, h))
        return max(2, (w * scale).roundToInt()) to max(2, (h * scale).roundToInt())
    }

    suspend fun render(
        src: Bitmap,
        params: EffectParams,
        faces: List<FaceLandmarks>,
        maxSide: Int,
        mask: SubjectMask? = null,
    ): Bitmap = withContext(dispatcher) {
        ensureGl()
        val prog = program!!
        val cap = min(maxSide, maxTex)
        val (w, h) = outputSize(src.width, src.height, params.crop, cap)
        val tex = textureFor(src)

        val fbTex = IntArray(1)
        val fbo = IntArray(1)
        GLES30.glGenTextures(1, fbTex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbTex[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fbTex[0], 0)
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) { "FBO incomplete" }

        // person mask (small) uploaded for this render only
        val maskIds = IntArray(1)
        if (mask != null && params.needsMask) {
            GLES30.glGenTextures(1, maskIds, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskIds[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            val mb = ByteBuffer.allocateDirect(mask.data.size).order(ByteOrder.nativeOrder())
            mb.put(mask.data); mb.position(0)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, mask.w, mask.h, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, mb)
        }

        GLES30.glViewport(0, 0, w, h)
        val aspect = src.width.toFloat() / src.height
        // Bitmap rows start at the top, so flip v when sampling.
        prog.draw(Mat3.flipV(), aspect, w, h, params, params.crop, faces, true, GLES30.GL_TEXTURE_2D, tex, maskIds[0])

        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, fbo, 0)
        GLES30.glDeleteTextures(1, fbTex, 0)
        if (maskIds[0] != 0) GLES30.glDeleteTextures(1, maskIds, 0)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        buf.rewind()
        out.copyPixelsFromBuffer(buf)
        out
    }
}
