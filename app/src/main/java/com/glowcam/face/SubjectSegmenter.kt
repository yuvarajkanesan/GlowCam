package com.glowcam.face

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max

/** Person mask: [data] holds one byte (0..255 confidence) per pixel, row 0 = top of the upright image. */
class SubjectMask(val w: Int, val h: Int, val data: ByteArray)

/** Finds the person in a frame / photo so filters can treat subject and background differently. */
class SubjectSegmenter {
    private val live = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build(),
    )
    private val still = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build(),
    )

    /** Live path. Does not close [image]; calls [onDone] when finished with it. */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun processFrame(image: ImageProxy, mirror: Boolean, onResult: (SubjectMask?) -> Unit, onDone: () -> Unit) {
        val media = image.image
        if (media == null) {
            onResult(null); onDone(); return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        live.process(input)
            .addOnSuccessListener { onResult(toMask(it, mirror, smooth = true)) }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { onDone() }
    }

    /** Still-image path (capture, editor). */
    suspend fun segment(bitmap: Bitmap, mirror: Boolean = false): SubjectMask? {
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > 1024) {
            val s = 1024f / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt().coerceAtLeast(1), (bitmap.height * s).toInt().coerceAtLeast(1), true)
        } else bitmap
        return try {
            toMask(still.process(InputImage.fromBitmap(scaled, 0)).await(), mirror)
        } catch (e: Exception) {
            null
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private var prevLive: ByteArray? = null

    private fun toMask(m: SegmentationMask, mirror: Boolean, smooth: Boolean = false): SubjectMask {
        val w = m.width
        val h = m.height
        val fb = m.buffer.asFloatBuffer()
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = fb.get(y * w + x).coerceIn(0f, 1f)
                val dx = if (mirror) w - 1 - x else x
                out[y * w + dx] = (v * 255f).toInt().toByte()
            }
        }
        if (smooth) {
            // blend with the previous live mask so the edge does not flicker from frame to frame
            val prev = prevLive
            if (prev != null && prev.size == out.size) {
                for (i in out.indices) {
                    out[i] = (((out[i].toInt() and 0xFF) * 0.65f + (prev[i].toInt() and 0xFF) * 0.35f).toInt()).toByte()
                }
            }
            prevLive = out.copyOf()
        }
        return SubjectMask(w, h, out)
    }

    fun close() {
        live.close()
        still.close()
    }
}
