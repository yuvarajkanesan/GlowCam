package com.glowcam.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glowcam.camera.MediaSaver
import com.glowcam.face.FaceLandmarks
import com.glowcam.face.FaceTracker
import com.glowcam.face.SubjectMask
import com.glowcam.face.SubjectSegmenter
import com.glowcam.gl.Blemish
import com.glowcam.gl.CropRect
import com.glowcam.gl.EffectParams
import com.glowcam.gl.Filters
import com.glowcam.gl.OfflineRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private enum class Tool(val label: String) { BEAUTY("Beauty"), MAKEUP("Makeup"), FILTER("Filters"), LOOKS("Looks"), BACKGROUND("Background"), ADJUST("Adjust"), RETOUCH("Retouch"), TEXT("Text"), CROP("Crop") }

private enum class RetouchTool { BLEMISH, TEETH, CIRCLES, REDEYE }

private data class Snapshot(val params: EffectParams, val rot: Int, val flip: Boolean, val overlays: List<Overlay> = emptyList())

private fun transform(src: Bitmap, rot: Int, flip: Boolean): Bitmap {
    if (rot == 0 && !flip) return src
    val m = Matrix()
    if (flip) m.postScale(-1f, 1f)
    m.postRotate(rot * 90f)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

@Composable
fun EditorScreen(uri: Uri, onClose: () -> Unit, onSaved: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracker = remember { FaceTracker() }
    val segmenter = remember { SubjectSegmenter() }
    DisposableEffect(Unit) { onDispose { tracker.close(); segmenter.close() } }

    var original by remember { mutableStateOf<Bitmap?>(null) }
    var rot by remember { mutableStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var params by remember { mutableStateOf(EffectParams()) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var faces by remember { mutableStateOf<List<FaceLandmarks>>(emptyList()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var mask by remember { mutableStateOf<SubjectMask?>(null) }
    var thumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var showOriginal by remember { mutableStateOf(false) }

    var tool by remember { mutableStateOf(Tool.BEAUTY) }
    var retouch by remember { mutableStateOf(RetouchTool.BLEMISH) }
    var brush by remember { mutableStateOf(0.03f) }
    var cropDraft by remember { mutableStateOf(NRect(0f, 0f, 1f, 1f)) }
    var cropRatio by remember { mutableStateOf<Float?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var exporting by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    var overlays by remember { mutableStateOf<List<Overlay>>(emptyList()) }
    var selectedOverlay by remember { mutableStateOf<Int?>(null) }
    var nextOverlayId by remember { mutableStateOf(1) }
    var undo by remember { mutableStateOf(listOf(Snapshot(EffectParams(), 0, false))) }
    var redo by remember { mutableStateOf<List<Snapshot>>(emptyList()) }

    fun commit() {
        val cur = Snapshot(params, rot, flip, overlays)
        if (undo.lastOrNull() != cur) {
            undo = undo + cur
            redo = emptyList()
        }
    }
    fun restore(s: Snapshot) {
        params = s.params; rot = s.rot; flip = s.flip; overlays = s.overlays
        cropDraft = NRect.from(s.params.crop)
    }
    fun doUndo() {
        if (undo.size > 1) {
            val popped = undo.last()
            undo = undo.dropLast(1)
            redo = redo + popped
            restore(undo.last())
        }
    }
    fun doRedo() {
        redo.lastOrNull()?.let {
            redo = redo.dropLast(1)
            undo = undo + it
            restore(it)
        }
    }

    LaunchedEffect(uri) {
        original = withContext(Dispatchers.IO) { ImageLoad.loadBitmap(context, uri, 4096) }
        if (original == null) {
            Toast.makeText(context, "Could not open this image", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    LaunchedEffect(original, rot, flip) {
        val o = original ?: return@LaunchedEffect
        source = withContext(Dispatchers.Default) { transform(o, rot, flip) }
    }
    LaunchedEffect(source) {
        faces = emptyList()
        source?.let { faces = tracker.detect(it) }
    }
    // Person mask, found once per photo as soon as a subject-aware filter is chosen.
    val wantsMask = params.needsMask
    LaunchedEffect(source, wantsMask) {
        mask = null
        if (wantsMask) source?.let { mask = segmenter.segment(it) }
    }
    // Real filter previews rendered from the photo itself (small, filter only).
    LaunchedEffect(original) {
        val o = original ?: return@LaunchedEffect
        val small = withContext(Dispatchers.Default) {
            val s = 160f / maxOf(o.width, o.height)
            Bitmap.createScaledBitmap(o, (o.width * s).toInt().coerceAtLeast(2), (o.height * s).toInt().coerceAtLeast(2), true)
        }
        val smallMask = segmenter.segment(small)
        val m = HashMap<String, Bitmap>()
        for (f in Filters.all) {
            m[f.id] = OfflineRenderer.render(small, EffectParams(filter = f), emptyList(), 120, smallMask)
        }
        thumbs = m
    }

    val inCrop = tool == Tool.CROP
    val effective = when {
        showOriginal -> EffectParams(crop = params.crop, straightenDeg = params.straightenDeg).let { if (inCrop) it.copy(crop = CropRect()) else it }
        inCrop -> params.copy(crop = CropRect())
        else -> params
    }
    LaunchedEffect(source, effective, faces, mask) {
        val s = source ?: return@LaunchedEffect
        delay(25)
        preview = OfflineRenderer.render(s, effective, faces, 1440, mask)
    }

    val srcAspect = source?.let { it.width.toFloat() / it.height } ?: 1f

    fun doExport(maxSide: Int) {
        val s = source ?: return
        showExport = false
        exporting = true
        scope.launch {
            try {
                val subject = if (params.needsMask) (mask ?: segmenter.segment(s)) else null
                val rendered = OfflineRenderer.render(s, params, faces, maxSide, subject)
                val out = withContext(Dispatchers.Default) { drawOverlays(rendered, overlays) }
                val saved = withContext(Dispatchers.IO) { MediaSaver.saveJpeg(context, out, 97) }
                onSaved(saved)
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                exporting = false
            }
        }
    }

    /** Maps a tap inside the displayed image to source uv (inverse of crop + straighten). */
    fun tapToSource(nx: Float, nyTop: Float): Pair<Float, Float> {
        val c = params.crop
        var px = c.x + nx * c.w
        var py = c.y + (1f - nyTop) * c.h
        if (params.straightenDeg != 0f) {
            val rad = Math.toRadians(params.straightenDeg.toDouble())
            val qx = (px - 0.5f) * srcAspect
            val qy = py - 0.5f
            val cs = cos(rad).toFloat(); val sn = sin(rad).toFloat()
            px = (cs * qx - sn * qy) / srcAspect + 0.5f
            py = (sn * qx + cs * qy) + 0.5f
        }
        return px to py
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        // ---------- top bar ----------
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(Icons.Rounded.Close, "Close", onClose)
            Box(Modifier.weight(1f))
            GlassButton(Icons.Rounded.Undo, "Undo", { doUndo() }, enabled = undo.size > 1)
            GlassButton(Icons.Rounded.Redo, "Redo", { doRedo() }, enabled = redo.isNotEmpty())
            GlassButton(
                Icons.Rounded.RestartAlt, "Reset all",
                { params = EffectParams(); rot = 0; flip = false; overlays = emptyList(); selectedOverlay = null; cropDraft = NRect(0f, 0f, 1f, 1f); commit() },
            )
            GradientButton("Save", Icons.Rounded.Check, { showExport = true })
        }

        // ---------- image ----------
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { boxSize = it },
            contentAlignment = Alignment.Center,
        ) {
            val pv = preview
            if (pv == null) {
                CircularProgressIndicator(color = Pink)
            } else {
                Image(pv.asImageBitmap(), null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                val pad = 6f * context.resources.displayMetrics.density
                val imgRect = fitRect(boxSize.width - 2 * pad, boxSize.height - 2 * pad, pv.width.toFloat() / pv.height)
                    .translate(pad, pad)

                if (!inCrop && overlays.isNotEmpty()) {
                    OverlayLayer(
                        overlays, selectedOverlay, imgRect, interactive = tool == Tool.TEXT,
                        onSelect = { selectedOverlay = it },
                        onChange = { upd -> overlays = overlays.map { if (it.id == upd.id) upd else it } },
                    )
                }
                if (tool == Tool.RETOUCH && retouch == RetouchTool.BLEMISH) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(params.crop, params.straightenDeg, imgRect, brush, srcAspect) {
                            detectTapGestures { off ->
                                val nx = (off.x - imgRect.left) / imgRect.width
                                val ny = (off.y - imgRect.top) / imgRect.height
                                if (nx in 0f..1f && ny in 0f..1f) {
                                    val (sx, sy) = tapToSource(nx, ny)
                                    if (params.blemishes.size < 16) {
                                        params = params.copy(blemishes = params.blemishes + Blemish(sx, sy, brush * params.crop.h.coerceAtLeast(0.2f)))
                                        commit()
                                    }
                                }
                            }
                        },
                    )
                }
                if (inCrop) {
                    CropOverlay(
                        rect = cropDraft, imgRect = imgRect, ratio = cropRatio, srcAspect = srcAspect,
                        onChange = { cropDraft = it },
                        onFinished = { params = params.copy(crop = cropDraft.toCrop()); commit() },
                    )
                }
            }
            // hold to compare with the original
            Box(
                Modifier.align(Alignment.BottomEnd).padding(14.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            showOriginal = true
                            tryAwaitRelease()
                            showOriginal = false
                        })
                    },
            ) {
                GlassButton(Icons.Rounded.Compare, "Hold to see the original", {}, selected = showOriginal, size = 46.dp)
            }
            if (exporting) {
                Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Pink)
                        Text("Rendering full resolution…", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }

        // ---------- tool panel ----------
        Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().sheet().padding(horizontal = 16.dp)
                    .heightIn(max = 250.dp).verticalScroll(rememberScrollState()).animateContentSize(),
            ) {
                SheetHandle()
                when (tool) {
                    Tool.BEAUTY -> {
                        LabeledSlider("Smooth skin", params.smooth, { params = params.copy(smooth = it) }, onFinished = { commit() })
                        LabeledSlider("Brighten", params.brighten, { params = params.copy(brighten = it) }, onFinished = { commit() })
                        LabeledSlider("Slim face", params.slim, { params = params.copy(slim = it) }, onFinished = { commit() })
                        LabeledSlider("Big eyes", params.eyes, { params = params.copy(eyes = it) }, onFinished = { commit() })
                        LabeledSlider("Jaw shape", params.jaw, { params = params.copy(jaw = it) }, onFinished = { commit() })
                        if (faces.isEmpty() && source != null) NoFaceHint()
                    }
                    Tool.TEXT -> OverlayPanel(
                        selected = overlays.firstOrNull { it.id == selectedOverlay },
                        onAddText = { t, c ->
                            overlays = overlays + Overlay(nextOverlayId, t, color = c); selectedOverlay = nextOverlayId; nextOverlayId++; commit()
                        },
                        onAddEmoji = { em ->
                            overlays = overlays + Overlay(nextOverlayId, em, scale = 1.8f, emoji = true); selectedOverlay = nextOverlayId; nextOverlayId++; commit()
                        },
                        onChange = { upd -> overlays = overlays.map { if (it.id == upd.id) upd else it } },
                        onDelete = { id -> overlays = overlays.filter { it.id != id }; selectedOverlay = null; commit() },
                        onCommit = { commit() },
                    )
                    Tool.MAKEUP -> {
                        MakeupPanel(params, { params = it }, { commit() })
                        if (faces.isEmpty() && source != null) NoFaceHint()
                    }
                    Tool.LOOKS -> LooksPanel(params, { params = it }, { commit() })
                    Tool.BACKGROUND -> {
                        BackgroundPanel(params, { params = it }, { commit() })
                        if (mask == null && params.bgMode != 0 && source != null) {
                            Text("Finding the person\u2026", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Tool.FILTER -> FilterPicker(
                        params.filter, params.filterIntensity,
                        onSelect = { params = params.copy(filter = it) },
                        onIntensity = { params = params.copy(filterIntensity = it) },
                        onFinished = { commit() },
                        thumbs = thumbs,
                    )
                    Tool.ADJUST -> {
                        val signed: (Float) -> String = { (it * 100).toInt().toString() }
                        LabeledSlider("Brightness", params.brightness, { params = params.copy(brightness = it) }, range = -1f..1f, onFinished = { commit() }, display = signed)
                        LabeledSlider("Contrast", params.contrast, { params = params.copy(contrast = it) }, range = -1f..1f, onFinished = { commit() }, display = signed)
                        LabeledSlider("Saturation", params.saturation, { params = params.copy(saturation = it) }, range = -1f..1f, onFinished = { commit() }, display = signed)
                        LabeledSlider("Blur", params.blur, { params = params.copy(blur = it) }, onFinished = { commit() })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            GlowChip("Full blur", !params.radialBlur) { params = params.copy(radialBlur = false); commit() }
                            GlowChip("Focus centre", params.radialBlur) { params = params.copy(radialBlur = true); commit() }
                        }
                        LabeledSlider("Vignette", params.vignette, { params = params.copy(vignette = it) }, onFinished = { commit() })
                    }
                    Tool.RETOUCH -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlowChip("Blemish", retouch == RetouchTool.BLEMISH) { retouch = RetouchTool.BLEMISH }
                            GlowChip("Teeth", retouch == RetouchTool.TEETH) { retouch = RetouchTool.TEETH }
                            GlowChip("Dark circles", retouch == RetouchTool.CIRCLES) { retouch = RetouchTool.CIRCLES }
                            GlowChip("Red-eye", retouch == RetouchTool.REDEYE) { retouch = RetouchTool.REDEYE }
                        }
                        when (retouch) {
                            RetouchTool.BLEMISH -> {
                                Text(
                                    "Tap a spot on the photo to remove it (${params.blemishes.size}/16).",
                                    color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp),
                                )
                                LabeledSlider("Brush size", brush, { brush = it }, range = 0.01f..0.08f, display = { (it * 1000).toInt().toString() })
                                GlowChip("Clear all spots") { params = params.copy(blemishes = emptyList()); commit() }
                            }
                            RetouchTool.TEETH -> {
                                LabeledSlider("Whiten", params.teeth, { params = params.copy(teeth = it) }, onFinished = { commit() })
                                if (faces.isEmpty() && source != null) NoFaceHint()
                            }
                            RetouchTool.CIRCLES -> {
                                LabeledSlider("Strength", params.darkCircles, { params = params.copy(darkCircles = it) }, onFinished = { commit() })
                                if (faces.isEmpty() && source != null) NoFaceHint()
                            }
                            RetouchTool.REDEYE -> {
                                LabeledSlider("Strength", params.redEye, { params = params.copy(redEye = it) }, onFinished = { commit() })
                                if (faces.isEmpty() && source != null) NoFaceHint()
                            }
                        }
                    }
                    Tool.CROP -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fun setRatio(r: Float?) {
                                cropRatio = r
                                val rect = if (r == null) NRect(0f, 0f, 1f, 1f) else ratioRect(r, srcAspect)
                                cropDraft = rect
                                params = params.copy(crop = rect.toCrop())
                                commit()
                            }
                            GlowChip("Free", cropRatio == null) { setRatio(null) }
                            GlowChip("1:1", cropRatio == 1f) { setRatio(1f) }
                            GlowChip("4:3", cropRatio == 4f / 3f) { setRatio(4f / 3f) }
                            GlowChip("3:4", cropRatio == 3f / 4f) { setRatio(3f / 4f) }
                            GlowChip("16:9", cropRatio == 16f / 9f) { setRatio(16f / 9f) }
                            GlowChip("9:16", cropRatio == 9f / 16f) { setRatio(9f / 16f) }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlowChip("Rotate 90°", icon = Icons.Rounded.RotateRight) {
                                rot = (rot + 1) % 4
                                params = params.copy(crop = CropRect(), blemishes = emptyList(), straightenDeg = 0f)
                                cropDraft = NRect(0f, 0f, 1f, 1f); commit()
                            }
                            GlowChip("Flip", icon = Icons.Rounded.Flip) {
                                flip = !flip
                                params = params.copy(crop = CropRect(), blemishes = emptyList())
                                cropDraft = NRect(0f, 0f, 1f, 1f); commit()
                            }
                            GlowChip("Reset crop") {
                                cropRatio = null; cropDraft = NRect(0f, 0f, 1f, 1f)
                                params = params.copy(crop = CropRect(), straightenDeg = 0f); commit()
                            }
                        }
                        LabeledSlider(
                            "Straighten", params.straightenDeg,
                            {
                                val c = inscribedCrop(it, srcAspect)
                                params = params.copy(straightenDeg = it, crop = c)
                                cropDraft = NRect.from(c)
                            },
                            range = -15f..15f, onFinished = { commit() }, display = { "%.1f°".format(it) },
                        )
                    }
                }
            }
            Row(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().background(SheetBg).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                val icons = mapOf(
                    Tool.BEAUTY to Icons.Rounded.Face,
                    Tool.MAKEUP to Icons.Rounded.Brush,
                    Tool.LOOKS to Icons.Rounded.AutoAwesome,
                    Tool.BACKGROUND to Icons.Rounded.Wallpaper,
                    Tool.FILTER to Icons.Rounded.FilterVintage,
                    Tool.ADJUST to Icons.Rounded.Tune,
                    Tool.RETOUCH to Icons.Rounded.AutoFixHigh,
                    Tool.TEXT to Icons.Rounded.TextFields,
                    Tool.CROP to Icons.Rounded.Crop,
                )
                for (t in Tool.values()) {
                    ToolTab(icons.getValue(t), t.label, tool == t, {
                        tool = t
                        if (t == Tool.CROP) cropDraft = NRect.from(params.crop)
                    })
                }
            }
        }
    }

    if (showExport) {
        val s = source
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Save photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (ow, oh) = if (s != null) OfflineRenderer.outputSize(s.width, s.height, params.crop, 4096) else (0 to 0)
                    Text("Choose a size. Original is $ow × $oh pixels (JPEG quality 95).", fontSize = 13.sp)
                    TextButton(onClick = { doExport(1080) }) { Text("Standard – 1080 px") }
                    TextButton(onClick = { doExport(2560) }) { Text("High – 2560 px") }
                    TextButton(onClick = { doExport(4096) }) { Text("Original – best quality") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExport = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoFaceHint() {
    Text(
        "No face found in this photo, so face tools won't change anything.",
        color = Color(0xFFFFB4B4), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** Centered crop of pixel ratio [ratio] (w/h) covering as much of the frame as possible. */
private fun ratioRect(ratio: Float, srcAspect: Float): NRect {
    var w = 1f
    var h = srcAspect / ratio
    if (h > 1f) {
        h = 1f
        w = ratio / srcAspect
    }
    return NRect((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
}
