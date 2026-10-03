package com.glowcam.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glowcam.camera.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private data class Cell(val x: Float, val y: Float, val w: Float, val h: Float)

/** Layout choices for [n] photos, each as cells in a unit square (gaps are applied later). */
private fun layoutsFor(n: Int): List<List<Cell>> = when (n) {
    1 -> listOf(listOf(Cell(0f, 0f, 1f, 1f)))
    2 -> listOf(
        listOf(Cell(0f, 0f, .5f, 1f), Cell(.5f, 0f, .5f, 1f)),
        listOf(Cell(0f, 0f, 1f, .5f), Cell(0f, .5f, 1f, .5f)),
    )
    3 -> listOf(
        listOf(Cell(0f, 0f, .6f, 1f), Cell(.6f, 0f, .4f, .5f), Cell(.6f, .5f, .4f, .5f)),
        listOf(Cell(0f, 0f, 1f, .6f), Cell(0f, .6f, .5f, .4f), Cell(.5f, .6f, .5f, .4f)),
        listOf(Cell(0f, 0f, 1f / 3, 1f), Cell(1f / 3, 0f, 1f / 3, 1f), Cell(2f / 3, 0f, 1f / 3, 1f)),
    )
    4 -> listOf(
        listOf(Cell(0f, 0f, .5f, .5f), Cell(.5f, 0f, .5f, .5f), Cell(0f, .5f, .5f, .5f), Cell(.5f, .5f, .5f, .5f)),
        listOf(Cell(0f, 0f, .6f, 1f), Cell(.6f, 0f, .4f, 1f / 3), Cell(.6f, 1f / 3, .4f, 1f / 3), Cell(.6f, 2f / 3, .4f, 1f / 3)),
    )
    5 -> listOf(
        listOf(Cell(0f, 0f, .5f, .5f), Cell(.5f, 0f, .5f, .5f), Cell(0f, .5f, 1f / 3, .5f), Cell(1f / 3, .5f, 1f / 3, .5f), Cell(2f / 3, .5f, 1f / 3, .5f)),
        listOf(Cell(0f, 0f, 1f, .5f), Cell(0f, .5f, .25f, .5f), Cell(.25f, .5f, .25f, .5f), Cell(.5f, .5f, .25f, .5f), Cell(.75f, .5f, .25f, .5f)),
    )
    else -> listOf(
        listOf(Cell(0f, 0f, 1f / 3, .5f), Cell(1f / 3, 0f, 1f / 3, .5f), Cell(2f / 3, 0f, 1f / 3, .5f), Cell(0f, .5f, 1f / 3, .5f), Cell(1f / 3, .5f, 1f / 3, .5f), Cell(2f / 3, .5f, 1f / 3, .5f)),
        listOf(Cell(0f, 0f, .5f, 1f / 3), Cell(.5f, 0f, .5f, 1f / 3), Cell(0f, 1f / 3, .5f, 1f / 3), Cell(.5f, 1f / 3, .5f, 1f / 3), Cell(0f, 2f / 3, .5f, 1f / 3), Cell(.5f, 2f / 3, .5f, 1f / 3)),
    )
}

private val BG_COLORS = listOf(0xFFFFFFFF, 0xFF111111, 0xFFFFC1D9, 0xFFFFE8CC, 0xFFCFE8FF, 0xFFD8F3DC).map { it.toInt() }

/** Cell rectangle in a canvas of [w] x [h] pixels, inset by [gap] on all sides. */
private fun cellRect(c: Cell, w: Float, h: Float, gap: Float): RectF {
    val l = c.x * w + gap / 2f
    val t = c.y * h + gap / 2f
    return RectF(l + (if (c.x == 0f) gap / 2f else 0f), t + (if (c.y == 0f) gap / 2f else 0f),
        (c.x + c.w) * w - gap / 2f - (if (c.x + c.w >= 0.999f) gap / 2f else 0f),
        (c.y + c.h) * h - gap / 2f - (if (c.y + c.h >= 0.999f) gap / 2f else 0f))
}

@Composable
fun CollageScreen(onBack: () -> Unit, onSaved: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var layoutIndex by remember { mutableIntStateOf(0) }
    var aspectIndex by remember { mutableIntStateOf(0) }
    var gap by remember { mutableFloatStateOf(0.03f) }
    var radius by remember { mutableFloatStateOf(0.03f) }
    var bg by remember { mutableIntStateOf(BG_COLORS[0]) }
    var saving by remember { mutableStateOf(false) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { picked ->
        if (picked.isNotEmpty()) { uris = picked; layoutIndex = 0 }
    }
    val aspects = listOf("1:1" to 1f, "4:5" to 0.8f, "9:16" to 0.5625f)
    val aspect = aspects[aspectIndex].second
    val layouts = remember(uris.size) { if (uris.isEmpty()) emptyList() else layoutsFor(uris.size) }
    val cells = layouts.getOrNull(layoutIndex.coerceIn(0, (layouts.size - 1).coerceAtLeast(0))) ?: emptyList()

    LaunchedEffect(uris) {
        bitmaps = withContext(Dispatchers.IO) { uris.mapNotNull { ImageLoad.loadBitmap(context, it, 1080) } }
    }

    Column(Modifier.fillMaxSize().background(Ink), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassButton(Icons.Rounded.ArrowBack, "Back", onBack)
            Text("Collage", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (bitmaps.isNotEmpty() && bitmaps.size == uris.size) {
                GradientButton("Save", Icons.Rounded.Check, {
                    saving = true
                    scope.launch {
                        try {
                            val out = withContext(Dispatchers.IO) { renderCollage(context, uris, cells, aspect, gap, radius, bg) }
                            val saved = withContext(Dispatchers.IO) { MediaSaver.saveJpeg(context, out, 97) }
                            onSaved(saved)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Couldn't save the collage: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            saving = false
                        }
                    }
                })
            }
        }

        if (uris.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Rounded.AddPhotoAlternate, null, tint = TextDim, modifier = Modifier.size(40.dp))
                }
                Text("Make a collage", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Pick 2 to 6 photos and choose a layout.", color = TextDim, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                GradientButton("Choose photos", Icons.Rounded.AddPhotoAlternate, {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
        } else {
            // preview
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val boxAspect = maxWidth / maxHeight
                    val modifier = if (boxAspect > aspect) Modifier.fillMaxSize().aspectRatio(aspect) else Modifier.fillMaxWidth().aspectRatio(aspect)
                    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(bg))) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val wPx = with(LocalDensity.current) { maxWidth.toPx() }
                            val hPx = with(LocalDensity.current) { maxHeight.toPx() }
                            val gapPx = gap * wPx
                            cells.forEachIndexed { i, cell ->
                                val bmp = bitmaps.getOrNull(i)
                                val r = cellRect(cell, wPx, hPx, gapPx)
                                Box(
                                    Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                                        .size(with(LocalDensity.current) { r.width().toDp() }, with(LocalDensity.current) { r.height().toDp() })
                                        .clip(RoundedCornerShape(with(LocalDensity.current) { (radius * wPx).toDp() }))
                                        .background(Color(0xFF2A2A33)),
                                ) {
                                    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
                if (saving || bitmaps.size != uris.size) CircularProgressIndicator(color = Pink)
            }

            // controls
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().sheet().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 10.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SheetHandle()
                Text("Layout", color = TextDim, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    layouts.forEachIndexed { i, cl ->
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF))
                                .border(if (i == layoutIndex) 3.dp else 1.dp, if (i == layoutIndex) Pink else Color(0x44FFFFFF), RoundedCornerShape(10.dp))
                                .clickable { layoutIndex = i }.padding(6.dp),
                        ) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val s = with(LocalDensity.current) { maxWidth.toPx() }
                                for (c in cl) {
                                    val rr = cellRect(c, s, s, 3f)
                                    Box(
                                        Modifier.offset { IntOffset(rr.left.roundToInt(), rr.top.roundToInt()) }
                                            .size(with(LocalDensity.current) { rr.width().toDp() }, with(LocalDensity.current) { rr.height().toDp() })
                                            .background(Color(0xCCFFFFFF), RoundedCornerShape(2.dp)),
                                    )
                                }
                            }
                        }
                    }
                    GlowChip("Change photos", icon = Icons.Rounded.AddPhotoAlternate) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Text("Shape", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    aspects.forEachIndexed { i, a -> GlowChip(a.first, aspectIndex == i) { aspectIndex = i } }
                }
                LabeledSlider("Spacing", gap, { gap = it }, range = 0f..0.08f, display = { (it * 1000).roundToInt().toString() })
                LabeledSlider("Corners", radius, { radius = it }, range = 0f..0.08f, display = { (it * 1000).roundToInt().toString() })
                Text("Background", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    for (c in BG_COLORS) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == bg) 3.dp else 1.dp, if (c == bg) Pink else Color(0x55FFFFFF), CircleShape)
                                .clickable { bg = c },
                        )
                    }
                }
            }
        }
    }
}

private fun renderCollage(
    context: android.content.Context, uris: List<Uri>, cells: List<Cell>, aspect: Float, gap: Float, radius: Float, bg: Int,
): Bitmap {
    val w = 2160
    val h = (w / aspect).roundToInt()
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(bg)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val gapPx = gap * w
    val rad = radius * w
    cells.forEachIndexed { i, cell ->
        val src = ImageLoad.loadBitmap(context, uris[i], 2160) ?: return@forEachIndexed
        val dst = cellRect(cell, w.toFloat(), h.toFloat(), gapPx)
        // centre-crop the photo to the cell
        val cellAspect = dst.width() / dst.height()
        val srcAspect = src.width.toFloat() / src.height
        val sw: Float; val sh: Float
        if (srcAspect > cellAspect) { sh = src.height.toFloat(); sw = sh * cellAspect } else { sw = src.width.toFloat(); sh = sw / cellAspect }
        val sl = (src.width - sw) / 2f
        val st = (src.height - sh) / 2f
        canvas.save()
        val path = Path().apply { addRoundRect(dst, rad, rad, Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(src, android.graphics.Rect(sl.roundToInt(), st.roundToInt(), (sl + sw).roundToInt(), (st + sh).roundToInt()), dst, paint)
        canvas.restore()
        src.recycle()
    }
    return out
}

@Suppress("unused")
private val keepMax = max(0, 0)
