package com.glowcam.camera

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaActionSound
import android.media.SoundPool
import com.glowcam.R

/**
 * Camera sounds with three levels: 0 off, 1 soft (a quiet click played by the app), 2 normal (the
 * phone's own camera sound). Both follow the phone's silent and vibrate modes.
 */
class ShutterSound(context: Context) {
    private val system = MediaActionSound()
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val softId: Int = pool.load(context, R.raw.shutter_soft, 1)
    @Volatile private var ready = false

    init {
        pool.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
    }

    private fun soft(rate: Float) {
        if (ready) pool.play(softId, SOFT_VOLUME, SOFT_VOLUME, 1, 0, rate)
    }

    fun photo(level: Int) {
        when (level) {
            1 -> soft(1.0f)
            2 -> system.play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    fun videoStart(level: Int) {
        when (level) {
            1 -> soft(1.15f)
            2 -> system.play(MediaActionSound.START_VIDEO_RECORDING)
        }
    }

    fun videoStop(level: Int) {
        when (level) {
            1 -> soft(0.85f)
            2 -> system.play(MediaActionSound.STOP_VIDEO_RECORDING)
        }
    }

    fun release() {
        pool.release()
        system.release()
    }

    private companion object {
        const val SOFT_VOLUME = 0.28f
    }
}
