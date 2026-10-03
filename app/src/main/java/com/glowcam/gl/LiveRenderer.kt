package com.glowcam.gl

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.glowcam.face.FaceLandmarks
import com.glowcam.face.SubjectMask
import java.util.concurrent.Executor

/**
 * Owns the GL thread. Camera frames arrive on a SurfaceTexture, are run through the shared effect
 * shader and drawn to the on-screen SurfaceView and (while recording) to the video encoder surface.
 */
class LiveRenderer {
    private val thread = HandlerThread("glow-gl").also { it.start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }

    private var egl: EglCore? = null
    private var program: EffectProgram? = null
    private var pbuffer: EGLSurface? = null
    private var display: EGLSurface? = null
    private var dispW = 0
    private var dispH = 0

    private var oesTex = 0
    private var cameraTexture: SurfaceTexture? = null
    private var bufW = 0
    private var bufH = 0
    private var rotation = 0
    private val stMat = FloatArray(16)

    private var encSurface: EGLSurface? = null
    private var encW = 0
    private var encH = 0
    private var recT0 = 0L

    // person mask for subject-aware filters, uploaded as a texture on the GL thread
    private var maskTex = 0
    private var uploadedMask: SubjectMask? = null
    private var maskBuf: java.nio.ByteBuffer? = null

    // one-shot request for a small unfiltered snapshot of the current view (used for filter previews)
    private var snapCb: ((android.graphics.Bitmap?) -> Unit)? = null
    private var snapW = 0
    private var snapH = 0

    @Volatile var mask: SubjectMask? = null
    @Volatile var params = EffectParams()
    @Volatile var faces: List<FaceLandmarks> = emptyList()
    @Volatile var mirror = true
    @Volatile var mirrorSelfie = true
    @Volatile var released = false

    /** True if OpenGL could not be set up on this device (the preview cannot work then). */
    @Volatile var failed = false
        private set

    init {
        handler.post {
            try {
                val e = EglCore(recordable = true)
                egl = e
                pbuffer = e.createPbuffer(1, 1).also { e.makeCurrent(it) }
                program = EffectProgram(oes = true)
            } catch (t: Throwable) {
                Log.e(TAG, "GL init failed", t)
                failed = true
            }
        }
    }

    // ---------------- display surface ----------------

    fun attachDisplay(surface: Surface, w: Int, h: Int) {
        handler.post {
            val e = egl ?: return@post
            display?.let { e.destroySurface(it) }
            display = e.createWindowSurface(surface)
            dispW = w; dispH = h
        }
    }

    fun resizeDisplay(w: Int, h: Int) {
        handler.post { dispW = w; dispH = h }
    }

    fun detachDisplay() {
        handler.post {
            val e = egl ?: return@post
            pbuffer?.let { e.makeCurrent(it) }
            display?.let { e.destroySurface(it) }
            display = null
        }
    }

    // ---------------- camera input ----------------

    val surfaceProvider = Preview.SurfaceProvider { request -> onSurfaceRequested(request) }

    private fun onSurfaceRequested(request: SurfaceRequest) {
        handler.post {
            val e = egl
            if (e == null || released) {
                request.willNotProvideSurface()
                return@post
            }
            pbuffer?.let { e.makeCurrent(it) }
            val old = cameraTexture
            val tex = IntArray(1)
            GLES30.glGenTextures(1, tex, 0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            val oldTex = oesTex
            oesTex = tex[0]

            val st = SurfaceTexture(oesTex)
            st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            st.setOnFrameAvailableListener({ drawFrame() }, handler)
            cameraTexture = st
            bufW = request.resolution.width
            bufH = request.resolution.height

            val surface = Surface(st)
            request.setTransformationInfoListener(executor) { info ->
                rotation = info.rotationDegrees
            }
            request.provideSurface(surface, executor) {
                surface.release()
            }
            old?.release()
            if (oldTex != 0) GLES30.glDeleteTextures(1, intArrayOf(oldTex), 0)
        }
    }

    // ---------------- recording ----------------

    fun startRecording(surface: Surface, w: Int, h: Int, t0Nanos: Long) {
        handler.post {
            val e = egl ?: return@post
            encSurface = e.createWindowSurface(surface)
            encW = w; encH = h; recT0 = t0Nanos
        }
    }

    /** Stops feeding the encoder; [done] runs on the GL thread after the last frame was submitted. */
    fun stopRecording(done: () -> Unit) {
        handler.post {
            val e = egl
            encSurface?.let { e?.destroySurface(it) }
            encSurface = null
            done()
        }
    }

    // ---------------- drawing ----------------

    private fun drawFrame() {
        val e = egl ?: return
        val prog = program ?: return
        val st = cameraTexture ?: return
        if (released) return
        try {
            val keep = pbuffer
            if (display == null && encSurface == null && keep != null) e.makeCurrent(keep)
            st.updateTexImage()
        } catch (t: Throwable) {
            Log.w(TAG, "updateTexImage failed", t)
            return
        }
        st.getTransformMatrix(stMat)

        val upright = if (rotation % 180 == 0) bufW.toFloat() / bufH else bufH.toFloat() / bufW
        // The SurfaceTexture transform already maps upright (and, for the front camera, selfie-mirrored)
        // uv to buffer coordinates on this pipeline; verified on-device.
        // Front camera (selfie) was verified upright with the matrix alone. The rear camera needs a
        // fixed extra turn (REAR_EXTRA_ROTATION, degrees); calibrate it on a device if it looks off.
        // Front camera: the matrix gives the mirrored selfie view; flip it back when mirroring is off.
        val adjust = if (mirror && !mirrorSelfie) Mat3.mirrorX()
        else Mat3.rotateAroundCenter(if (mirror) 0 else REAR_EXTRA_ROTATION)
        val buf = Mat3.mul(Mat3.fromSurfaceTexture(stMat), adjust)

        val p = params
        val f = faces
        val maskId = if (p.needsMask) updateMaskTexture(mask) else 0

        snapCb?.let { cb ->
            snapCb = null
            cb(try { renderSnapshot(prog, buf, upright) } catch (t: Throwable) { Log.w(TAG, "snapshot failed", t); null })
        }

        display?.let { d ->
            try {
                e.makeCurrent(d)
                GLES30.glViewport(0, 0, dispW, dispH)
                val crop = EffectProgram.coverCrop(upright, dispW.toFloat() / dispH)
                prog.draw(buf, upright, dispW, dispH, p.withoutGeometry(), crop, f, false,
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex, maskId)
                if (!e.swap(d)) {
                    e.destroySurface(d); display = null
                }
            } catch (t: Throwable) {
                Log.w(TAG, "display draw failed", t)
            }
        }

        encSurface?.let { s ->
            try {
                e.makeCurrent(s)
                GLES30.glViewport(0, 0, encW, encH)
                val crop = EffectProgram.coverCrop(upright, encW.toFloat() / encH)
                prog.draw(buf, upright, encW, encH, p.withoutGeometry(), crop, f, false,
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex, maskId)
                e.setPresentationTime(s, System.nanoTime() - recT0)
                e.swap(s)
            } catch (t: Throwable) {
                Log.w(TAG, "encoder draw failed", t)
            }
        }
    }

    /** Asks for a small, unfiltered copy of the current view (mirrored like the preview). */
    fun requestSnapshot(w: Int, h: Int, cb: (android.graphics.Bitmap?) -> Unit) {
        handler.post { snapW = w; snapH = h; snapCb = cb }
    }

    private fun renderSnapshot(prog: EffectProgram, buf: FloatArray, upright: Float): android.graphics.Bitmap {
        val w = snapW
        val h = snapH
        pbuffer?.let { egl?.makeCurrent(it) }
        val tex = IntArray(1)
        val fbo = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex[0], 0)
        GLES30.glViewport(0, 0, w, h)
        val crop = EffectProgram.coverCrop(upright, w.toFloat() / h)
        prog.draw(buf, upright, w, h, EffectParams(), crop, emptyList(), true, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex, 0)
        val bytes = java.nio.ByteBuffer.allocateDirect(w * h * 4).order(java.nio.ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bytes)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, fbo, 0)
        GLES30.glDeleteTextures(1, tex, 0)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        bytes.rewind()
        bmp.copyPixelsFromBuffer(bytes)
        return bmp
    }

    /** Uploads [m] into the mask texture if it changed. Returns the texture id, or 0 when there is no mask. */
    private fun updateMaskTexture(m: SubjectMask?): Int {
        if (m == null) return 0
        if (m !== uploadedMask || maskTex == 0) {
            if (maskTex == 0) {
                val t = IntArray(1)
                GLES30.glGenTextures(1, t, 0)
                maskTex = t[0]
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTex)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            }
            var buf = maskBuf
            if (buf == null || buf.capacity() != m.data.size) {
                buf = java.nio.ByteBuffer.allocateDirect(m.data.size)
                maskBuf = buf
            }
            buf!!.clear(); buf.put(m.data); buf.position(0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTex)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, m.w, m.h, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buf)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            uploadedMask = m
        }
        return maskTex
    }

    fun release() {
        released = true
        handler.post {
            cameraTexture?.release()
            cameraTexture = null
            program?.release()
            egl?.release()
            egl = null
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "LiveRenderer"
        const val REAR_EXTRA_ROTATION = 0
    }
}
