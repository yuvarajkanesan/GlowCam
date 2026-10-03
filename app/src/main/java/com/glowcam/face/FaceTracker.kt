package com.glowcam.face

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import kotlinx.coroutines.tasks.await
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/** Detects the face mesh (live frames and still bitmaps) and smooths it to avoid jitter. */
class FaceTracker {
    private val detector = FaceMeshDetection.getClient()
    private val smoother = LandmarkSmoother()

    /** Live path: call from the analysis executor. Does not close [image]; calls [onDone] when finished with it. */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun processFrame(image: ImageProxy, mirror: Boolean, onResult: (List<FaceLandmarks>) -> Unit, onDone: () -> Unit) {
        val media = image.image
        if (media == null) {
            onDone()
            return
        }
        val rot = image.imageInfo.rotationDegrees
        val w = if (rot % 180 == 0) media.width else media.height
        val h = if (rot % 180 == 0) media.height else media.width
        val input = InputImage.fromMediaImage(media, rot)
        detector.process(input)
            .addOnSuccessListener { meshes ->
                val raw = toLandmarks(meshes, w, h, mirror)
                onResult(smoother.smooth(raw, System.nanoTime()))
            }
            .addOnFailureListener { onResult(emptyList()) }
            .addOnCompleteListener { onDone() }
    }

    /** Still-image path (capture, editor). No smoothing. */
    suspend fun detect(bitmap: Bitmap, mirror: Boolean = false): List<FaceLandmarks> {
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > 1024) {
            val s = 1024f / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt().coerceAtLeast(1), (bitmap.height * s).toInt().coerceAtLeast(1), true)
        } else bitmap
        return try {
            val meshes = detector.process(InputImage.fromBitmap(scaled, 0)).await()
            toLandmarks(meshes, scaled.width, scaled.height, mirror)
        } catch (e: Exception) {
            emptyList()
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun toLandmarks(meshes: List<FaceMesh>, w: Int, h: Int, mirror: Boolean): List<FaceLandmarks> =
        meshes.mapNotNull { mesh ->
            val pts = mesh.allPoints
            if (pts.size < FaceLandmarks.COUNT) return@mapNotNull null
            val xy = FloatArray(FaceLandmarks.COUNT * 2)
            for (i in 0 until FaceLandmarks.COUNT) {
                val pos = pts[i].position
                var x = pos.x / w
                if (mirror) x = 1f - x
                xy[i * 2] = x
                xy[i * 2 + 1] = 1f - pos.y / h
            }
            FaceLandmarks(xy)
        }.sortedBy { f -> f.x(1) }

    fun close() = detector.close()
}

/** One-Euro-style adaptive low-pass: heavy smoothing when still, light when moving fast. */
class LandmarkSmoother(
    private val minCutoff: Float = 2.0f,
    private val beta: Float = 12.0f,
) {
    private var prev: List<FloatArray>? = null
    private var prevT = 0L

    fun smooth(faces: List<FaceLandmarks>, tNanos: Long): List<FaceLandmarks> {
        val last = prev
        if (last == null || last.size != faces.size) {
            prev = faces.map { it.xy.copyOf() }
            prevT = tNanos
            return faces
        }
        val dt = ((tNanos - prevT) / 1e9f).coerceIn(0.001f, 0.5f)
        prevT = tNanos
        val out = ArrayList<FaceLandmarks>(faces.size)
        for ((fi, face) in faces.withIndex()) {
            val p = last[fi]
            val cur = face.xy
            for (i in cur.indices) {
                val speed = abs(cur[i] - p[i]) / dt
                val cutoff = minCutoff + beta * speed
                val tau = 1f / (2f * PI.toFloat() * cutoff)
                val alpha = 1f / (1f + tau / dt)
                p[i] += alpha * (cur[i] - p[i])
            }
            out.add(FaceLandmarks(p.copyOf()))
        }
        return out
    }
}
