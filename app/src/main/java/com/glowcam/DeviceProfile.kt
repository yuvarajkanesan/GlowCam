package com.glowcam

import android.app.ActivityManager
import android.content.Context

/**
 * Classifies the phone (memory, CPU count) so heavy work can be dialled down on weaker devices:
 * cheaper shader sampling, slower face analysis, smaller photo limits and a lower default video size.
 */
object DeviceProfile {
    enum class Tier { LOW, MEDIUM, HIGH }

    @Volatile private var cached: Tier? = null

    fun tier(context: Context): Tier = cached ?: compute(context).also { cached = it }

    private fun compute(context: Context): Tier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = info.totalMem / 1_073_741_824.0
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            am.isLowRamDevice || gb < 2.5 || (cores <= 4 && gb < 4.0) -> Tier.LOW
            gb < 5.5 -> Tier.MEDIUM
            else -> Tier.HIGH
        }
    }

    /** Shader sample divisor: 2 halves the number of blur / smoothing taps on weak GPUs. */
    fun lod(context: Context) = if (tier(context) == Tier.LOW) 2 else 1

    /** Face / person analysis runs on every Nth camera frame. */
    fun analysisEvery(context: Context) = if (tier(context) == Tier.LOW) 3 else 1

    /** Longest side allowed for a processed photo. */
    fun maxPhotoSide(context: Context) = if (tier(context) == Tier.LOW) 3000 else 4096

    /** Longest side loaded into the editor. */
    fun maxEditSide(context: Context) = when (tier(context)) {
        Tier.LOW -> 2048
        Tier.MEDIUM -> 3072
        Tier.HIGH -> 4096
    }

    /** Sharp mode takes one shot instead of three on low-memory phones. */
    fun burstShots(context: Context) = if (tier(context) == Tier.LOW) 1 else 3

    fun defaultVideoRes(context: Context) = if (tier(context) == Tier.LOW) 720 else 1080
}
