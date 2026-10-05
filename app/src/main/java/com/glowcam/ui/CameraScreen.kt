package com.glowcam.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.glowcam.AppSettings
import com.glowcam.DeviceProfile
import com.glowcam.camera.CameraCaps
import com.glowcam.camera.CameraEngine
import com.glowcam.camera.LocationTagger
import com.glowcam.camera.MediaSaver
import com.glowcam.camera.MotionMonitor
import com.glowcam.camera.ProSettings
import com.glowcam.camera.Sharpness
import com.glowcam.camera.ShootMode
import com.glowcam.gl.EffectParams
import com.glowcam.gl.Filters
import com.glowcam.gl.OfflineRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Panel { BEAUTY, MAKEUP, FILTERS, LOOKS, BACKGROUND, CAPTURE }

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun CameraScreen(
    settings: AppSettings,
    last: Pair<Uri, Boolean>?,
    onLastChanged: (Pair<Uri, Boolean>) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: (Uri) -> Unit,
    onCollage: () -> Unit,
    shutterTrigger: Int,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var camGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var micGranted by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camGranted = it }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onImport(uri) }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) { if (!camGranted) camLauncher.launch(Manifest.permission.CAMERA) }

    if (!camGranted) {
        PermissionScreen(
            onAllow = { camLauncher.launch(Manifest.permission.CAMERA) },
            onSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")),
                )
            },
        )
        return
    }

    val engine = remember { CameraEngine(context) }
    DisposableEffect(Unit) { onDispose { engine.release() } }
    val motion = remember { MotionMonitor(context) }

    // ---- settings-backed state ----
    val front = settings.front.value
    val sharp = settings.sharp.value
    val maxRes = settings.original.value
    val touchShoot = settings.touchShoot.value

    // ---- screen state ----
    var aspect by remember { mutableStateOf(Aspect.R4_3) }
    var params by remember { mutableStateOf(EffectParams()) }
    var compare by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.PHOTO) }
    var timer by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) } // closed until the user opens a tool
    var countdown by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var glow by remember { mutableStateOf(false) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var faceCount by remember { mutableIntStateOf(0) }
    var shaky by remember { mutableStateOf(false) }
    var thumbs by remember { mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap()) }
    var thumbsFront by remember { mutableStateOf(front) }
    var lastShotCount by remember { mutableIntStateOf(0) }

    // zoom / exposure / focus
    var zoom by remember { mutableFloatStateOf(1f) }
    var zoomMin by remember { mutableFloatStateOf(1f) }
    var zoomMax by remember { mutableFloatStateOf(1f) }
    var zoomShown by remember { mutableIntStateOf(0) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var exposureValue by remember { mutableFloatStateOf(0f) }
    var exposureTick by remember { mutableIntStateOf(0) }
    var exposureVisible by remember { mutableStateOf(false) }
    var aeLocked by remember { mutableStateOf(false) }
    var vfSize by remember { mutableStateOf(IntSize.Zero) }

    // shooting mode / Pro
    var caps by remember { mutableStateOf(CameraCaps()) }
    var shootMode by remember { mutableStateOf(ShootMode.AUTO) }
    var proOn by remember { mutableStateOf(false) }
    var isoT by remember { mutableFloatStateOf(0.35f) }
    var shutterT by remember { mutableFloatStateOf(0.6f) }
    var awb by remember { mutableIntStateOf(1) }

    // aids
    var roll by remember { mutableFloatStateOf(0f) }
    var flatPhone by remember { mutableStateOf(false) }
    var hist by remember { mutableStateOf(IntArray(64)) }

    fun say(s: String) { message = s }
    /** Android 9 and older: saving to Pictures / Movies needs the storage permission. */
    fun ensureStorage(): Boolean {
        if (MediaSaver.hasStorageAccess(context)) return true
        storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        say("Allow storage access so GlowCam can save your photos and videos")
        return false
    }
    LaunchedEffect(camGranted) { if (camGranted) ensureStorage() }
    var fatalError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2500)
        if (engine.renderer.failed) fatalError = true
    }
    fun proSettings(): ProSettings? =
        if (proOn && caps.manual) ProSettings(ProMath.iso(isoT, caps.isoRange), ProMath.ns(shutterT, caps.exposureNsRange), awb) else null

    fun videoSize(): Pair<Int, Int> {
        val vw = settings.videoRes.value
        return vw to (vw / aspect.ratio).roundToInt() / 2 * 2
    }

    LaunchedEffect(params, compare) { engine.setParams(if (compare) EffectParams() else params) }
    LaunchedEffect(settings.mirrorSelfie.value) { engine.mirrorSelfie = settings.mirrorSelfie.value }
    LaunchedEffect(settings.shutterLevel.value) { engine.soundLevel = settings.shutterLevel.value }
    LaunchedEffect(settings.showHistogram.value) { engine.wantHistogram = settings.showHistogram.value }
    val mpOptions = remember(front, aspect.wide) { engine.photoMpOptions(front, aspect.wide) }
    val mpChoice = settings.photoMp.value
    LaunchedEffect(front, aspect.wide, sharp, maxRes, mode, settings.videoRes.value, settings.videoFps.value, mpChoice) {
        engine.mirrorSelfie = settings.mirrorSelfie.value
        try {
            engine.bind(owner, front, aspect.wide, sharp, maxRes && !front, mode == Mode.VIDEO, settings.videoRes.value, settings.videoFps.value, mpChoice)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            say("The camera isn't available right now. Close other camera apps and try again.")
            return@LaunchedEffect
        }
        caps = engine.caps
        if (!engine.analysisAvailable) say("This camera can't track faces, so beauty effects are off here.")
        zoom = 1f
        zoomMin = engine.zoomRange.start
        zoomMax = engine.zoomRange.endInclusive
        engine.applyShooting(shootMode, proSettings())
        engine.setExposure((exposureValue * engine.evRange.last).roundToInt())
        if (maxRes && !front) {
            delay(500)
            engine.captureSize()?.let { sz ->
                val mp = sz.width.toLong() * sz.height / 1_000_000L
                message = if (mp >= 40) "Max resolution on: ${sz.width} × ${sz.height} (~$mp MP)"
                else "Original quality on: untouched ${sz.width} × ${sz.height} camera files. Samsung keeps the 200 MP mode for its own Camera app."
            }
        }
    }
    LaunchedEffect(shootMode, proOn, isoT, shutterT, awb, caps) { engine.applyShooting(shootMode, proSettings()) }
    LaunchedEffect(exposureValue) { engine.setExposure((exposureValue * engine.evRange.last).roundToInt()) }
    LaunchedEffect(recording, flash, front) { engine.setTorch(recording && flash && !front) }
    LaunchedEffect(recording) {
        recSecs = 0
        while (recording) {
            delay(1000)
            recSecs++
        }
    }
    LaunchedEffect(exposureTick) {
        if (exposureTick > 0) {
            exposureVisible = true
            delay(4000)
            exposureVisible = false
        }
    }
    LaunchedEffect(zoomShown) {
        if (zoomShown > 0) delay(1200)
    }
    LaunchedEffect(sharp) {
        while (true) {
            shaky = sharp && motion.rate > 0.45f
            delay(250)
        }
    }
    LaunchedEffect(settings.showLevel.value) {
        while (settings.showLevel.value) {
            roll = motion.rollDeg; flatPhone = motion.flat
            delay(80)
        }
    }
    LaunchedEffect(settings.showHistogram.value) {
        while (settings.showHistogram.value) {
            hist = engine.histogram
            delay(250)
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            message = null
        }
    }
    LaunchedEffect(params.needsFaces, front) {
        while (true) {
            faceCount = if (params.needsFaces) engine.renderer.faces.size else -1
            delay(400)
        }
    }

    // Realistic filter previews: every filter applied to a live snapshot of what the camera sees
    // (including the person cut-out for the subject filters). Refreshes while the Filters sheet is open.
    LaunchedEffect(panel, front, recording) {
        if (panel != Panel.FILTERS || recording) return@LaunchedEffect
        if (thumbsFront != front) { thumbs = emptyMap(); thumbsFront = front }
        while (true) {
            val snap = engine.snapshot(150, 200)
            if (snap != null) {
                val m = engine.segmenter.segment(snap)
                val map = HashMap<String, android.graphics.Bitmap>()
                for (f in Filters.all) map[f.id] = OfflineRenderer.render(snap, EffectParams(filter = f), emptyList(), 200, m)
                thumbs = map
            }
            delay(4000)
        }
    }

    fun setScreenBright(on: Boolean) {
        val w = context.findActivity()?.window ?: return
        val lp = w.attributes
        lp.screenBrightness = if (on) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        w.attributes = lp
    }

    suspend fun doCapture(fast: Boolean = false) {
        if (busy || !ensureStorage()) return
        busy = true
        try {
            val useGlow = front && flash
            if (useGlow) {
                glow = true; setScreenBright(true); delay(350)
            }
            val bigCapture = engine.captureSize()?.let { it.width.toLong() * it.height > 30_000_000L } == true
            val full = (maxRes && !front) || bigCapture
            val night = shootMode == ShootMode.NIGHT
            // one shot, taken immediately (no waiting to be steady, no multi-shot burst); Night mode still waits
            val shots = 1
            val sound = true
            if (night && !fast) {
                say("Night shot, hold still…")
                if (!motion.awaitSteady(2200, 0.08f)) say("Hold steady for a sharper photo")
            }
            val quality = settings.photoQuality.value
            val p0 = (if (compare) EffectParams() else params).withoutGeometry()
            // no effects: save the camera's original JPEG untouched at the full chosen MP (any size, 4:3 or 16:9).
            // Filters and 1:1 crops go through the bitmap path below, which reduces the size.
            val untouched = p0.isIdentity && aspect != Aspect.R1_1 && (full || !(front && settings.mirrorSelfie.value))
            if (untouched) {
                val saved = engine.takePhotoToGallery(flash && !front, sound)
                glow = false; setScreenBright(false)
                if (saved != null) {
                    if (settings.locationTag.value) withContext(Dispatchers.IO) { LocationTagger.tag(context, saved) }
                    onLastChanged(saved to false)
                    lastShotCount++
                    if (!fast) say("Saved the camera’s original photo, untouched")
                } else say("Couldn't take the photo. Please try again.")
                return
            }
            var bmp: android.graphics.Bitmap? = null
            var bestScore = -1.0
            for (i in 0 until shots) {
                val b = engine.takePhoto(flash && !front, aspect.ratio, playSound = sound && i == 0) ?: continue
                if (shots == 1) { bmp = b; break }
                // keep only the sharpest frame so memory stays low
                val sc = Sharpness.score(b)
                if (sc > bestScore) { bmp?.recycle(); bmp = b; bestScore = sc } else b.recycle()
            }
            glow = false; setScreenBright(false)
            if (bmp == null) {
                say("Couldn't take the photo. Please try again."); return
            }
            val p = p0
            val faces = if (p.needsFaces) engine.tracker.detect(bmp) else emptyList()
            val subject = if (p.needsMask) engine.segmenter.segment(bmp) else null
            // no effects active: keep the camera's original pixels untouched
            val out = if (p.isIdentity) bmp else OfflineRenderer.render(bmp, p, faces, DeviceProfile.maxPhotoSide(context), subject)
            val uri = withContext(Dispatchers.IO) {
                MediaSaver.saveJpeg(context, out, quality).also { if (settings.locationTag.value) LocationTagger.tag(context, it) }
            }
            onLastChanged(uri to false)
            lastShotCount++
            if (!fast) say("Saved to Pictures/GlowCam")
        } catch (e: OutOfMemoryError) {
            say("Not enough memory for that photo. Close other apps or turn off Original quality.")
        } catch (e: Exception) {
            say("Capture failed: ${e.message}")
        } finally {
            busy = false; glow = false; setScreenBright(false)
        }
    }

    /** Swipe up = rear camera, swipe down = front camera. */
    fun onSwipeCamera(up: Boolean) {
        val wantFront = !up
        // read the live setting: the gesture handler outlives recompositions, so a captured `front` would be stale
        if (recording || busy || wantFront == settings.front.value) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        settings.front.set(wantFront)
        say(if (wantFront) "Front camera" else "Rear camera")
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        engine.stopVideo { uri ->
            scope.launch(Dispatchers.Main) {
                if (uri != null) {
                    onLastChanged(uri to true)
                    say("Video saved to Movies/GlowCam")
                } else say("Recording was too short")
            }
        }
    }

    fun startRecording() {
        if (!ensureStorage()) return
        try {
            val (vw, vh) = videoSize()
            engine.startVideo(vw, vh, micGranted, settings.videoFps.value)
            recording = true
            if (!micGranted) say("Recording without sound (microphone not allowed)")
        } catch (e: Exception) {
            say("Can't record: ${e.message}")
        }
    }

    // press and hold the shutter: keep taking photos until released (max 30)
    val holding = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    fun startBurst() {
        if (mode != Mode.PHOTO || recording || countdown > 0 || !holding.compareAndSet(false, true)) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            var n = 0
            while (holding.get() && n < 30) {
                if (busy) { delay(30); continue }
                val before = lastShotCount
                doCapture(fast = true)
                if (lastShotCount != before) n++
            }
            holding.set(false)
            if (n > 0) say("Burst: $n photos saved")
        }
    }

    fun onShutter() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (recording) {
            stopRecording(); return
        }
        if (countdownJob?.isActive == true) {
            countdownJob?.cancel(); countdown = 0; return
        }
        countdownJob = scope.launch {
            if (timer > 0) {
                for (s in timer downTo 1) {
                    countdown = s
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    delay(1000)
                }
                countdown = 0
            }
            if (mode == Mode.PHOTO) doCapture() else startRecording()
        }
    }

    // Volume keys act as the shutter.
    LaunchedEffect(shutterTrigger) { if (shutterTrigger > 0 && settings.volumeShutter.value && !busy) onShutter() }

    // Never leave a recording half-written when the app goes to the background.
    DisposableEffect(Unit) { motion.start(); onDispose { motion.stop() } }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) motion.start()
            if (e == Lifecycle.Event.ON_STOP) {
                motion.stop()
                countdownJob?.cancel(); countdown = 0
                stopRecording()
                setScreenBright(false)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (fatalError) {
        Column(
            Modifier.fillMaxSize().background(Ink).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("The preview couldn't start", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "This phone's graphics driver doesn't support GlowCam's live effects. You can still edit existing photos.",
                color = TextDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
            )
            GradientButton("Edit a photo", null, { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        }
        return
    }

    val statusTop = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screenW = maxWidth
        val screenH = maxHeight
        val topBarH = statusTop + 64.dp
        val previewH = minOf(screenW / aspect.ratio, screenH)
        val density = LocalDensity.current

        // ---------- viewfinder ----------
        Box(
            Modifier
                .align(if (aspect.wide) Alignment.Center else Alignment.TopCenter)
                .padding(top = if (aspect.wide) 0.dp else topBarH)
                .fillMaxWidth()
                .height(previewH)
                .onSizeChanged { vfSize = it },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) {}
                            override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) {
                                engine.renderer.attachDisplay(h.surface, w, hh)
                            }
                            override fun surfaceDestroyed(h: SurfaceHolder) {
                                engine.renderer.detachDisplay()
                            }
                        })
                    }
                },
            )
            GridOverlay(settings.gridStyle.value, Modifier.fillMaxSize())
            if (settings.showLevel.value) LevelOverlay(roll, flatPhone, Modifier.fillMaxSize())
            if (settings.showHistogram.value) HistogramOverlay(hist, Modifier.align(Alignment.TopStart).padding(10.dp))

            // gestures: pinch to zoom, tap to focus (or shoot), long press to lock focus + exposure
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        // two fingers pinch to zoom; one finger swiping up selects the rear camera, down the front
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var dx = 0f
                            var dy = 0f
                            var pinched = false
                            var switched = false
                            val threshold = 36.dp.toPx()
                            do {
                                val event = awaitPointerEvent()
                                val down = event.changes.filter { it.pressed }
                                if (down.size >= 2) {
                                    pinched = true
                                    val z = event.calculateZoom()
                                    if (z != 1f) {
                                        zoom = (zoom * z).coerceIn(zoomMin, zoomMax)
                                        engine.setZoom(zoom)
                                        zoomShown++
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (!pinched && !switched && down.size == 1) {
                                    val d = down[0].position - down[0].previousPosition
                                    dx += d.x; dy += d.y
                                    if (kotlin.math.abs(dy) > threshold && kotlin.math.abs(dy) > 1.3f * kotlin.math.abs(dx)) {
                                        switched = true
                                        event.changes.forEach { it.consume() }
                                        onSwipeCamera(dy < 0)
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(touchShoot, front) {
                        detectTapGestures(
                            onLongPress = { off ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                focusPoint = off
                                engine.focus(off.x, off.y, size.width, size.height, lock = true)
                                aeLocked = true
                                exposureTick++
                                say("Focus and exposure locked. Tap to unlock")
                            },
                            onTap = { off ->
                                if (aeLocked) {
                                    engine.unlockFocus(); aeLocked = false
                                }
                                if (touchShoot) onShutter() else {
                                    focusPoint = off
                                    engine.focus(off.x, off.y, size.width, size.height)
                                    exposureTick++
                                }
                            },
                        )
                    },
            )
            if (exposureVisible) {
                focusPoint?.let { f ->
                    Box(
                        Modifier.offset { IntOffset((f.x - 36.dp.toPx()).roundToInt(), (f.y - 36.dp.toPx()).roundToInt()) }
                            .size(72.dp).border(2.dp, if (aeLocked) Color(0xFFFFD166) else Pink, CircleShape),
                    )
                    val sliderW = with(density) { 52.dp.toPx() }
                    val right = f.x + with(density) { 56.dp.toPx() } + sliderW < vfSize.width
                    val sx = if (right) f.x + with(density) { 56.dp.toPx() } else f.x - with(density) { 56.dp.toPx() } - sliderW
                    val sy = (f.y - with(density) { 90.dp.toPx() }).coerceIn(0f, (vfSize.height - with(density) { 190.dp.toPx() }).coerceAtLeast(0f))
                    ExposureSlider(
                        exposureValue, { exposureValue = it }, { exposureTick++ },
                        Modifier.offset { IntOffset(sx.roundToInt(), sy.roundToInt()) },
                    )
                }
            }
            if (glow) {
                Box(Modifier.fillMaxSize().background(Color(0x66FFF1D6)).border(48.dp, Color(0xFFFFF1D6)))
            }
        }

        if (countdown > 0) {
            Text(
                countdown.toString(), color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ---------- top bar ----------
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(ScrimTop).statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton(
                    if (front) Icons.Rounded.LightMode else if (flash) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    if (front) "Screen glow" else "Flash", { flash = !flash }, selected = flash,
                )
                GlassButton(
                    Icons.Rounded.Timer, "Timer", { timer = when (timer) { 0 -> 3; 3 -> 5; 5 -> 10; else -> 0 } },
                    selected = timer > 0, badge = if (timer > 0) "${timer}s" else null,
                )
                GlassButton(
                    Icons.Rounded.AspectRatio, "Aspect ratio",
                    { aspect = Aspect.values()[(aspect.ordinal + 1) % Aspect.values().size] },
                    badge = aspect.label,
                )
                if (mode == Mode.PHOTO && !recording && mpOptions.size > 1) {
                    // tap to step down through the camera's megapixel sizes, wrapping back to the maximum
                    val std = remember(front, aspect.wide) { engine.defaultMp(front, aspect.wide) }
                    val shown = if (maxRes && !front) mpOptions.first() else if (mpChoice in mpOptions) mpChoice else std
                    MegapixelButton(
                        shown,
                        {
                            if (maxRes && !front) {
                                say("Turn off Original quality in Settings to pick a smaller size")
                            } else {
                                val i = mpOptions.indexOf(shown)
                                val next = mpOptions[(i + 1) % mpOptions.size]
                                settings.photoMp.set(next)
                                say(if (next >= 30) "Photo size: $next MP (full sensor, saved untouched)" else "Photo size: $next MP")
                            }
                        },
                        selected = shown != std,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                var importMenu by remember { mutableStateOf(false) }
                Box {
                    GlassButton(Icons.Rounded.PhotoLibrary, "Edit or collage", { importMenu = true })
                    androidx.compose.material3.DropdownMenu(
                        expanded = importMenu, onDismissRequest = { importMenu = false },
                        containerColor = Color(0xFF23232B),
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Edit a photo", color = Color.White) },
                            leadingIcon = { Icon(Icons.Rounded.Face, null, tint = Color.White) },
                            onClick = {
                                importMenu = false
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Make a collage", color = Color.White) },
                            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White) },
                            onClick = { importMenu = false; onCollage() },
                        )
                    }
                }
                GlassButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
        }

        // ---------- recording badge, zoom readout & messages ----------
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = topBarH + 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (recording) {
                Row(
                    Modifier.background(Danger, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(Color.White, CircleShape))
                    Text(
                        "  %02d:%02d".format(recSecs / 60, recSecs % 60), color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                LaunchedEffect(recSecs) { if (recSecs >= 60) stopRecording() }
            }
            AnimatedVisibility(message != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    message ?: "", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp).background(GlassStrong, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            AnimatedVisibility(shaky && !recording && message == null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Hold steady for a sharper photo",
                    color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp).background(GlassStrong, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
            AnimatedVisibility(faceCount == 0 && !shaky && !recording && message == null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Looking for a face… face the camera in good light",
                    color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp).background(GlassStrong, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        // ---------- bottom controls ----------
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(ScrimBottom).padding(bottom = navBottom + 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!recording) {
                ZoomChips(
                    zoom, zoomMin, zoomMax,
                    { z -> zoom = z; engine.setZoom(z) },
                    Modifier.padding(bottom = 8.dp),
                )
            }
            AnimatedVisibility(
                visible = panel != null && !recording,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().sheet().padding(horizontal = 16.dp).padding(bottom = 6.dp)
                        .heightIn(max = 300.dp).verticalScroll(rememberScrollState()).animateContentSize(),
                ) {
                    SheetHandle()
                    when (panel) {
                        Panel.BEAUTY -> {
                            LabeledSlider("Smooth skin", params.smooth, { params = params.copy(smooth = it) })
                            LabeledSlider("Brighten", params.brighten, { params = params.copy(brighten = it) })
                            LabeledSlider("Slim face", params.slim, { params = params.copy(slim = it) })
                            LabeledSlider("Big eyes", params.eyes, { params = params.copy(eyes = it) })
                            LabeledSlider("Jaw shape", params.jaw, { params = params.copy(jaw = it) })
                        }
                        Panel.MAKEUP -> MakeupPanel(params, { params = it })
                        Panel.LOOKS -> LooksPanel(params, { params = it; say("Look applied") })
                        Panel.BACKGROUND -> BackgroundPanel(params, { params = it })
                        Panel.FILTERS -> FilterPicker(
                            params.filter, params.filterIntensity,
                            onSelect = { params = params.copy(filter = it) },
                            onIntensity = { params = params.copy(filterIntensity = it) },
                            thumbs = thumbs,
                        )
                        Panel.CAPTURE -> CapturePanel(
                            settings = settings, caps = caps, front = front,
                            shootMode = shootMode,
                            onShootMode = {
                                shootMode = it
                                say(
                                    when (it) {
                                        ShootMode.AUTO -> "Auto"
                                        ShootMode.HDR -> "HDR on"
                                        ShootMode.NIGHT -> "Night mode: hold very still"
                                    },
                                )
                            },
                            proOn = proOn, onProOn = { proOn = it },
                            isoT = isoT, onIsoT = { isoT = it },
                            shutterT = shutterT, onShutterT = { shutterT = it },
                            awb = awb, onAwb = { awb = it },
                        )
                        null -> {}
                    }
                }
            }

            // tool tabs
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().background(SheetBg).horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolTab(Icons.Rounded.RestartAlt, "Reset", false, { params = EffectParams(); say("Effects reset") }, Modifier.width(68.dp))
                ToolTab(Icons.Rounded.Face, "Beauty", panel == Panel.BEAUTY, { panel = if (panel == Panel.BEAUTY) null else Panel.BEAUTY }, Modifier.width(68.dp))
                ToolTab(Icons.Rounded.Brush, "Makeup", panel == Panel.MAKEUP, { panel = if (panel == Panel.MAKEUP) null else Panel.MAKEUP }, Modifier.width(68.dp))
                ToolTab(Icons.Rounded.FilterVintage, "Filters", panel == Panel.FILTERS, { panel = if (panel == Panel.FILTERS) null else Panel.FILTERS }, Modifier.width(68.dp))
                ToolTab(Icons.Rounded.AutoAwesome, "Looks", panel == Panel.LOOKS, { panel = if (panel == Panel.LOOKS) null else Panel.LOOKS }, Modifier.width(68.dp))
                ToolTab(Icons.Rounded.Wallpaper, "Background", panel == Panel.BACKGROUND, { panel = if (panel == Panel.BACKGROUND) null else Panel.BACKGROUND }, Modifier.width(68.dp))
                ToolTab(Icons.Rounded.Tune, "Capture", panel == Panel.CAPTURE, { panel = if (panel == Panel.CAPTURE) null else Panel.CAPTURE }, Modifier.width(68.dp))
                ToolTab(
                    Icons.Rounded.Compare, "Compare", compare, null,
                    Modifier.width(68.dp).pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            compare = true
                            tryAwaitRelease()
                            compare = false
                        })
                    },
                )
            }

            // mode switch
            Box(Modifier.padding(top = 10.dp)) {
                ModeSwitch(mode, enabled = !recording) { m ->
                    mode = m
                    if (m == Mode.VIDEO && !micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            // shutter row
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumb = rememberBitmap(last?.first, last?.second == true, 200)
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF))
                        .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                        .clickable { onOpenGallery() },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.Crossfade(targetState = thumb, label = "thumb") { bmp ->
                        bmp?.let { Image(it.asImageBitmap(), "Latest photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    if (last?.second == true) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    if (last == null) Icon(Icons.Rounded.PhotoLibrary, null, tint = Color(0x88FFFFFF), modifier = Modifier.size(22.dp))
                }
                Shutter(
                    video = mode == Mode.VIDEO, recording = recording, progress = recSecs / 60f, busy = busy, onClick = ::onShutter,
                    onHoldStart = ::startBurst, onHoldEnd = { holding.set(false) },
                )
                GlassButton(Icons.Rounded.FlipCameraAndroid, "Switch camera", { settings.front.set(!front) }, size = 54.dp, enabled = !recording)
            }
        }
    }
}

@Suppress("unused")
private fun keepAbs() = abs(0)
