package com.glowcam.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.sqrt

/** Image sharpness: variance of the Laplacian on a downscaled copy. Higher = sharper. */
object Sharpness {
    fun score(bmp: Bitmap): Double {
        val w = 480
        val h = (bmp.height * w.toFloat() / bmp.width).toInt().coerceAtLeast(3)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bmp) small.recycle()
        val g = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            g[i] = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = 4f * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}

/** Watches the gyroscope so the app can tell when the phone is being shaken or moved. */
class MotionMonitor(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Sideways tilt of the phone in degrees (0 = upright and level; positive = tilted clockwise). */
    @Volatile var rollDeg = 0f
        private set

    /** True when the phone lies flat (face up or down), where a horizon line makes no sense. */
    @Volatile var flat = false
        private set

    /** Smoothed angular speed in rad/s (about 0.1 = very steady, above 0.4 = clearly shaking). */
    @Volatile var rate = 0f
        private set

    fun start() {
        gyro?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sm.unregisterListener(this)
        rate = 0f
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = e.values[0]
            val ay = e.values[1]
            val az = e.values[2]
            flat = kotlin.math.abs(az) > 8.5f
            val roll = Math.toDegrees(atan2(ax.toDouble(), ay.toDouble())).toFloat()
            rollDeg = rollDeg * 0.8f + roll * 0.2f
            return
        }
        val r = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        rate = rate * 0.7f + r * 0.3f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Waits (up to [maxMs]) until the phone has been still for a moment. Returns false on timeout. */
    suspend fun awaitSteady(maxMs: Long = 1100, threshold: Float = 0.14f): Boolean {
        if (gyro == null) return true
        val t0 = System.currentTimeMillis()
        var calm = 0
        while (System.currentTimeMillis() - t0 < maxMs) {
            calm = if (rate < threshold) calm + 1 else 0
            if (calm >= 3) return true
            delay(40)
        }
        return false
    }
}
