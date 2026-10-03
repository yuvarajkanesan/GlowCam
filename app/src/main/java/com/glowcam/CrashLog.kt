package com.glowcam

import android.content.Context
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Local crash reporting: the last crash is written to a file on the phone. Nothing is sent anywhere;
 * the user can choose to share the report from Settings if they want help.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                file(app).writeText(
                    "GlowCam crash report\n" +
                        "Time: ${DateFormat.getDateTimeInstance().format(Date())}\n" +
                        "Thread: ${t.name}\n" +
                        "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n\n" +
                        e.stackTraceToString(),
                )
            } catch (_: Exception) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}
