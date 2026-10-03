package com.glowcam.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface

/** Minimal EGL14 wrapper: one context, any number of window / pbuffer surfaces. */
class EglCore(recordable: Boolean) {
    val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val config: EGLConfig
    private val context: EGLContext

    init {
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val v = IntArray(2)
        check(EGL14.eglInitialize(display, v, 0, v, 1)) { "eglInitialize failed" }

        config = chooseConfig(recordable) ?: chooseConfig(false) ?: error("No EGL config")
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
    }

    private fun chooseConfig(recordable: Boolean): EGLConfig? {
        val attrs = ArrayList<Int>().apply {
            addAll(listOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8))
            addAll(listOf(EGL14.EGL_RENDERABLE_TYPE, 0x0040 /* EGL_OPENGL_ES3_BIT_KHR */))
            addAll(listOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT))
            if (recordable) addAll(listOf(0x3142 /* EGL_RECORDABLE_ANDROID */, 1))
            add(EGL14.EGL_NONE)
        }.toIntArray()
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        return if (EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, n, 0) && n[0] > 0) configs[0] else null
    }

    fun createWindowSurface(surface: Any): EGLSurface {
        val s = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(s != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        return s
    }

    fun createPbuffer(w: Int, h: Int): EGLSurface {
        val s = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, w, EGL14.EGL_HEIGHT, h, EGL14.EGL_NONE), 0,
        )
        check(s != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        return s
    }

    fun makeCurrent(s: EGLSurface) {
        check(EGL14.eglMakeCurrent(display, s, s, context)) { "eglMakeCurrent failed" }
    }

    fun swap(s: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, s)

    fun setPresentationTime(s: EGLSurface, nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, s, nanos)
    }

    fun destroySurface(s: EGLSurface) {
        EGL14.eglDestroySurface(display, s)
    }

    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
    }
}
