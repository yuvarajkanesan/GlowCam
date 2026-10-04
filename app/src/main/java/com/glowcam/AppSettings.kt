package com.glowcam

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BoolPref(private val p: SharedPreferences, private val key: String, def: Boolean) {
    var value by mutableStateOf(p.getBoolean(key, def))
        private set

    fun set(v: Boolean) {
        value = v
        p.edit().putBoolean(key, v).apply()
    }

    fun toggle() = set(!value)
}

class IntPref(private val p: SharedPreferences, private val key: String, def: Int) {
    var value by mutableStateOf(p.getInt(key, def))
        private set

    fun set(v: Int) {
        value = v
        p.edit().putInt(key, v).apply()
    }
}

/** All user settings, persisted. Values are Compose state so screens update immediately. */
class AppSettings(context: Context) {
    private val p = context.getSharedPreferences("glowcam_settings", Context.MODE_PRIVATE)

    val front = BoolPref(p, "front", false)             // remember last camera
    val mirrorSelfie = BoolPref(p, "mirror", true)
    /** Camera sound: 0 off, 1 soft (default), 2 normal system sound. */
    val shutterLevel = IntPref(p, "shutter_level", if (p.getBoolean("sound", true)) 1 else 0)
    val volumeShutter = BoolPref(p, "volume_shutter", true)
    val locationTag = BoolPref(p, "location_tag", false)
    val sharp = BoolPref(p, "sharp", true)
    val original = BoolPref(p, "original", false)
    val touchShoot = BoolPref(p, "touch_shoot", false)
    val showLevel = BoolPref(p, "level", false)
    val showHistogram = BoolPref(p, "histogram", false)

    /** Set once the first-run tips have been shown, so they appear only on a fresh install. */
    val tipsSeen = BoolPref(p, "tips_seen", false)

    /** 0 off, 1 thirds, 2 golden ratio, 3 diagonals. */
    val gridStyle = IntPref(p, "grid_style", 0)

    /** JPEG quality: 90, 95, 97 or 100. */
    val photoQuality = IntPref(p, "photo_quality", 97)

    /** Photo resolution in megapixels; 0 = the camera's maximum. */
    val photoMp = IntPref(p, "photo_mp", 0)

    /** Video height class: 720, 1080 or 2160 (4K). */
    val videoRes = IntPref(p, "video_res", DeviceProfile.defaultVideoRes(context))
    val videoFps = IntPref(p, "video_fps", 30)
}
