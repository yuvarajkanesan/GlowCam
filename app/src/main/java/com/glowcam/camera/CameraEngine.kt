package com.glowcam.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.glowcam.DeviceProfile
import com.glowcam.face.FaceTracker
import com.glowcam.face.SubjectSegmenter
import com.glowcam.gl.EffectParams
import com.glowcam.gl.LiveRenderer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

enum class ShootMode { AUTO, HDR, NIGHT }

/** Manual ("Pro") controls: ISO, shutter time in nanoseconds, and a CONTROL_AWB_MODE value. */
data class ProSettings(val iso: Int, val exposureNs: Long, val awb: Int)

/** What the camera hardware offers (read once per bind). */
data class CameraCaps(
    val hdr: Boolean = false,
    val manual: Boolean = false,
    val isoRange: IntRange = 100..800,
    val exposureNsRange: LongRange = 1_000_000L..100_000_000L,
    val fps60: Boolean = false,
)

/** Wires CameraX (preview, capture, analysis) to the GL renderer, face tracker and camera controls. */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraEngine(private val context: Context) {
    val renderer = LiveRenderer().also { it.lod = DeviceProfile.lod(context) }
    private val analysisEvery = DeviceProfile.analysisEvery(context)
    private var analysisCount = 0

    /** False when this camera cannot run face / person analysis next to preview and capture. */
    @Volatile var analysisAvailable = true
        private set
    val tracker = FaceTracker()
    val segmenter = SubjectSegmenter()

    private val bg = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val shutter = MediaActionSound()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var recorder: VideoRecorder? = null

    @Volatile private var front = true
    @Volatile private var needFaces = false
    @Volatile private var needMask = false
    @Volatile var wantHistogram = false
    @Volatile var histogram = IntArray(64)
        private set
    @Volatile var mirrorSelfie = true
        set(v) {
            field = v
            renderer.mirrorSelfie = v
        }

    private var frameNo = 0
    private var shootMode = ShootMode.AUTO
    private var pro: ProSettings? = null
    private var userEv = 0

    var caps = CameraCaps()
        private set

    fun setParams(p: EffectParams) {
        renderer.params = p
        needFaces = p.needsFaces
        needMask = p.needsMask
        if (!needFaces) renderer.faces = emptyList()
        if (!needMask) renderer.mask = null
    }

    private suspend fun cameraProvider(): ProcessCameraProvider = provider ?: suspendCoroutine { cont ->
        val f = ProcessCameraProvider.getInstance(context)
        f.addListener({ cont.resume(f.get().also { provider = it }) }, ContextCompat.getMainExecutor(context))
    }

    suspend fun bind(
        owner: LifecycleOwner,
        front: Boolean,
        wide: Boolean,
        sharp: Boolean = false,
        maxRes: Boolean = false,
        video: Boolean = false,
        videoRes: Int = 1080,
        fps: Int = 30,
    ) {
        val p = cameraProvider()
        this.front = front
        renderer.mirror = front
        renderer.mirrorSelfie = mirrorSelfie
        renderer.faces = emptyList()
        renderer.mask = null
        p.unbindAll()

        val ch = characteristics(front)
        caps = readCaps(ch)

        val strategy = AspectRatioStrategy(
            if (wide) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3,
            AspectRatioStrategy.FALLBACK_RULE_AUTO,
        )

        // Preview resolution follows the video quality setting while the Video tab is active.
        val previewSelector = ResolutionSelector.Builder().setAspectRatioStrategy(strategy).apply {
            if (video) {
                val target = when {
                    videoRes >= 2160 -> if (wide) Size(3840, 2160) else Size(4000, 3000)
                    videoRes >= 1080 -> if (wide) Size(1920, 1080) else Size(1440, 1080)
                    else -> if (wide) Size(1280, 720) else Size(960, 720)
                }
                setResolutionStrategy(ResolutionStrategy(target, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            }
        }.build()
        val previewBuilder = Preview.Builder().setResolutionSelector(previewSelector)
        if (video && fps >= 60 && caps.fps60) {
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(60, 60))
        }
        val preview = previewBuilder.build()
        preview.setSurfaceProvider(renderer.surfaceProvider)

        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(strategy)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    // 200 MP sensors only deliver full resolution in "maximum resolution" mode
                    .setAllowedResolutionMode(
                        if (maxRes && !front) ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                        else ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION,
                    )
                    .build(),
            )
        if (sharp) applySharpOptions(captureBuilder, ch)
        val capture = captureBuilder.build()
        imageCapture = capture

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(strategy)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { img: ImageProxy ->
            if (wantHistogram && frameNo++ % 3 == 0) computeHistogram(img)
            val wantFaces = needFaces
            val wantMask = needMask
            if (!wantFaces && !wantMask) {
                img.close()
                return@setAnalyzer
            }
            // weak phones analyse every 3rd frame to keep the preview smooth
            if (analysisEvery > 1 && analysisCount++ % analysisEvery != 0) {
                img.close()
                return@setAnalyzer
            }
            // close the frame once every detector that uses it has finished
            val pending = AtomicInteger((if (wantFaces) 1 else 0) + (if (wantMask) 1 else 0))
            val done = { if (pending.decrementAndGet() == 0) img.close() }
            val mirrored = front && mirrorSelfie
            if (wantFaces) tracker.processFrame(img, mirrored, { renderer.faces = it }, done)
            if (wantMask) segmenter.processFrame(img, mirrored, { renderer.mask = it }, done)
        }

        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        analysisAvailable = true
        camera = try {
            p.bindToLifecycle(owner, selector, preview, capture, analysis)
        } catch (e: IllegalArgumentException) {
            // Older cameras cannot run preview + capture + analysis together: drop face analysis, keep the camera.
            p.unbindAll()
            analysisAvailable = false
            p.bindToLifecycle(owner, selector, preview, capture)
        }
    }

    // ---------------- capabilities ----------------

    private fun readCaps(ch: CameraCharacteristics?): CameraCaps {
        if (ch == null) return CameraCaps()
        val scenes = ch.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
        val capabilities = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val iso = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exp = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val fps = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        return CameraCaps(
            hdr = scenes?.contains(CaptureRequest.CONTROL_SCENE_MODE_HDR) == true,
            manual = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true,
            isoRange = if (iso != null) iso.lower..iso.upper else 100..800,
            exposureNsRange = if (exp != null) exp.lower..minOf(exp.upper, 250_000_000L) else 1_000_000L..100_000_000L,
            fps60 = fps?.any { it.lower == 60 && it.upper == 60 } == true,
        )
    }

    // ---------------- zoom / exposure / focus ----------------

    val zoomRange: ClosedFloatingPointRange<Float>
        get() = camera?.cameraInfo?.zoomState?.value?.let { it.minZoomRatio..it.maxZoomRatio } ?: 1f..1f

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(zoomRange.start, zoomRange.endInclusive))
    }

    val evRange: IntRange
        get() = camera?.cameraInfo?.exposureState?.exposureCompensationRange?.let { it.lower..it.upper } ?: 0..0

    /** Exposure compensation in index steps (each step is 1/10 EV on this phone). */
    fun setExposure(index: Int) {
        userEv = index
        applyEv()
    }

    private fun applyEv() {
        val r = evRange
        val eff = (userEv + if (shootMode == ShootMode.NIGHT) 8 else 0).coerceIn(r.first, r.last)
        camera?.cameraControl?.setExposureCompensationIndex(eff)
    }

    fun focus(x: Float, y: Float, viewW: Int, viewH: Int, lock: Boolean = false) {
        val cam = camera ?: return
        val point = SurfaceOrientedMeteringPointFactory(viewW.toFloat(), viewH.toFloat()).createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).apply { if (lock) disableAutoCancel() }.build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun unlockFocus() {
        camera?.cameraControl?.cancelFocusAndMetering()
    }

    fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }

    /**
     * Applies HDR / Night / Pro (manual) settings to the live camera. Call after every bind.
     * HDR uses the camera's HDR scene mode; Night lets the camera use longer exposures and adds
     * brightness; Pro switches to manual ISO, shutter time and white balance.
     */
    fun applyShooting(mode: ShootMode, manual: ProSettings?) {
        shootMode = mode
        pro = manual
        val cam = camera ?: return
        val b = CaptureRequestOptions.Builder()
        if (manual != null) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manual.iso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manual.exposureNs)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(manual.exposureNs, 33_333_333L))
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, manual.awb)
        } else {
            when (mode) {
                ShootMode.HDR -> {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
                }
                ShootMode.NIGHT -> {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(15, 30))
                    b.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                }
                ShootMode.AUTO -> {}
            }
        }
        try {
            Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
        } catch (e: Exception) {
            android.util.Log.w("CameraEngine", "Camera does not accept these shooting options", e)
        }
        applyEv()
    }

    // ---------------- misc ----------------

    private fun computeHistogram(img: ImageProxy) {
        val plane = img.planes[0]
        val buf = plane.buffer
        val stride = plane.rowStride
        val bins = IntArray(64)
        var y = 0
        while (y < img.height) {
            var x = 0
            val start = y * stride
            while (x < img.width) {
                bins[(buf.get(start + x).toInt() and 0xFF) shr 2]++
                x += 4
            }
            y += 4
        }
        histogram = bins
    }

    /** Small unfiltered snapshot of the live view, or null if nothing arrived in time. */
    suspend fun snapshot(w: Int, h: Int): Bitmap? = kotlinx.coroutines.withTimeoutOrNull(1500) {
        kotlinx.coroutines.suspendCancellableCoroutine<Bitmap?> { cont ->
            renderer.requestSnapshot(w, h) { if (cont.isActive) cont.resume(it) }
        }
    }

    /**
     * Sharp mode: ask the camera for optical stabilisation and its best noise-reduction and edge-
     * enhancement modes, but only the ones this camera actually reports as supported.
     */
    private fun applySharpOptions(builder: ImageCapture.Builder, ch: CameraCharacteristics?) {
        ch ?: return
        val ext = Camera2Interop.Extender(builder)
        fun IntArray?.has(v: Int) = this?.contains(v) == true
        if (ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION).has(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
            ext.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        }
        if (ch.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES).has(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
            ext.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        }
        if (ch.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES).has(CaptureRequest.EDGE_MODE_HIGH_QUALITY)) {
            ext.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        }
    }

    /** Pixel size the photo capture is actually configured for (after binding). */
    fun captureSize(): Size? = imageCapture?.resolutionInfo?.resolution

    /**
     * Takes a photo and writes the camera's original JPEG straight to the gallery, untouched
     * (used by Original mode, so a large Bitmap never has to be created).
     */
    suspend fun takePhotoToGallery(rearFlash: Boolean, playSound: Boolean = true): Uri? = suspendCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resume(null); return@suspendCoroutine
        }
        capture.flashMode = if (rearFlash && !front) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        if (playSound) shutter.play(MediaActionSound.SHUTTER_CLICK)
        val values = MediaSaver.imageValues(pending = false)
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
        ).build()
        capture.takePicture(options, bg, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                cont.resume(output.savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resume(null)
            }
        })
    }

    /** Decodes a captured JPEG; very large frames are downsampled to about 12-16 MP. */
    private fun decodeCapture(image: ImageProxy): Bitmap {
        val pixels = image.width.toLong() * image.height
        if (pixels <= 30_000_000L || image.format != ImageFormat.JPEG) return image.toBitmap()
        val buf = image.planes[0].buffer
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        var sample = 1
        while (pixels / (sample.toLong() * sample) > 16_000_000L) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun characteristics(front: Boolean): CameraCharacteristics? = try {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        mgr.cameraIdList.map { mgr.getCameraCharacteristics(it) }.firstOrNull { it.get(CameraCharacteristics.LENS_FACING) == want }
    } catch (e: Exception) {
        null
    }

    /**
     * Takes a full-resolution photo and returns it upright (mirrored like the preview for selfies when
     * mirroring is on) and centre-cropped to [aspectWH] (width / height).
     */
    suspend fun takePhoto(rearFlash: Boolean, aspectWH: Float, playSound: Boolean = true): Bitmap? = suspendCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resume(null); return@suspendCoroutine
        }
        capture.flashMode = if (rearFlash && !front) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        if (playSound) shutter.play(MediaActionSound.SHUTTER_CLICK)
        capture.takePicture(bg, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = try {
                    val rot = image.imageInfo.rotationDegrees
                    val raw = decodeCapture(image)
                    val m = Matrix().apply {
                        postRotate(rot.toFloat())
                        if (front && mirrorSelfie) postScale(-1f, 1f)
                    }
                    val oriented = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    if (oriented !== raw) raw.recycle()
                    centerCrop(oriented, aspectWH)
                } catch (e: Throwable) {
                    null
                } finally {
                    image.close()
                }
                cont.resume(result)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resume(null)
            }
        })
    }

    fun startVideo(width: Int, height: Int, audio: Boolean, fps: Int = 30) {
        val rec = VideoRecorder(context, width, height, audio, fps)
        val surface = rec.start()
        recorder = rec
        renderer.startRecording(surface, width, height, rec.t0)
        shutter.play(MediaActionSound.START_VIDEO_RECORDING)
    }

    fun stopVideo(onDone: (Uri?) -> Unit) {
        val rec = recorder ?: return onDone(null)
        recorder = null
        shutter.play(MediaActionSound.STOP_VIDEO_RECORDING)
        renderer.stopRecording {
            Thread { onDone(rec.stop()) }.start()
        }
    }

    fun playShutter(enabled: Boolean) {
        if (enabled) shutter.play(MediaActionSound.SHUTTER_CLICK)
    }

    fun release() {
        recorder?.let { r -> renderer.stopRecording { Thread { r.stop() }.start() } }
        recorder = null
        provider?.unbindAll()
        renderer.release()
        tracker.close()
        segmenter.close()
        shutter.release()
        bg.shutdown()
        analysisExecutor.shutdown()
    }

    companion object {
        fun centerCrop(src: Bitmap, aspectWH: Float): Bitmap {
            val srcAspect = src.width.toFloat() / src.height
            if (kotlin.math.abs(srcAspect - aspectWH) < 0.005f) return src
            val (w, h) = if (srcAspect > aspectWH) {
                (src.height * aspectWH).roundToInt() to src.height
            } else {
                src.width to (src.width / aspectWH).roundToInt()
            }
            val out = Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h)
            if (out !== src) src.recycle()
            return out
        }
    }
}
