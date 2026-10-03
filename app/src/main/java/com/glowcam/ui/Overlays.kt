package com.glowcam.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A text label or emoji sticker. Position is the centre in the final image (0..1, top-left origin). */
data class Overlay(
    val id: Int,
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rot: Float = 0f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val emoji: Boolean = false,
)

private const val BASE = 0.075f // text height as a fraction of the image width at scale 1

@Composable
fun OverlayLayer(
    overlays: List<Overlay>,
    selectedId: Int?,
    imgRect: Rect,
    interactive: Boolean,
    onSelect: (Int?) -> Unit,
    onChange: (Overlay) -> Unit,
) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        for (o in overlays) {
            val cur by rememberUpdatedState(o)
            val cx = imgRect.left + o.x * imgRect.width
            val cy = imgRect.top + o.y * imgRect.height
            val sel = o.id == selectedId && interactive
            Text(
                o.text,
                color = Color(o.color),
                fontSize = with(density) { (imgRect.width * BASE * o.scale).toSp() },
                fontWeight = if (o.emoji) FontWeight.Normal else FontWeight.Bold,
                modifier = Modifier
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE))
                        layout(p.width, p.height) { p.place((cx - p.width / 2f).roundToInt(), (cy - p.height / 2f).roundToInt()) }
                    }
                    .graphicsLayer { rotationZ = o.rot }
                    .then(if (sel) Modifier.border(1.5.dp, Pink) else Modifier)
                    .then(
                        if (interactive) Modifier
                            .pointerInput(o.id) { detectTapGestures { onSelect(o.id) } }
                            .pointerInput(o.id) {
                                detectTransformGestures { _, pan, zoom, rotation ->
                                    val a = Math.toRadians(cur.rot.toDouble())
                                    val dx = (pan.x * cos(a) - pan.y * sin(a)).toFloat()
                                    val dy = (pan.x * sin(a) + pan.y * cos(a)).toFloat()
                                    onChange(
                                        cur.copy(
                                            x = (cur.x + dx / imgRect.width).coerceIn(0f, 1f),
                                            y = (cur.y + dy / imgRect.height).coerceIn(0f, 1f),
                                            scale = (cur.scale * zoom).coerceIn(0.3f, 8f),
                                            rot = cur.rot + rotation,
                                        ),
                                    )
                                }
                            }
                        else Modifier,
                    ),
            )
        }
    }
}

/** Draws the overlays onto a copy of [src] so exported photos match the preview. */
fun drawOverlays(src: Bitmap, overlays: List<Overlay>): Bitmap {
    if (overlays.isEmpty()) return src
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    for (o in overlays) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = out.width * BASE * o.scale
            color = o.color
            textAlign = Paint.Align.CENTER
            typeface = if (o.emoji) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        }
        canvas.save()
        canvas.translate(o.x * out.width, o.y * out.height)
        canvas.rotate(o.rot)
        val fm = paint.fontMetrics
        canvas.drawText(o.text, 0f, -(fm.ascent + fm.descent) / 2f, paint)
        canvas.restore()
    }
    return out
}

private val TEXT_COLORS = listOf(0xFFFFFFFF, 0xFF111111, 0xFFFF5FA2, 0xFFFFD166, 0xFF7AD3C1, 0xFF6CA8E8, 0xFFFF6F61).map { it.toInt() }
private val EMOJIS = listOf(
    "😍", "🥰", "😎", "🤩", "😘", "😇", "🔥", "✨",
    "💖", "🌸", "🌈", "⭐", "🎉", "🎈", "👑", "🦋",
    "🍀", "☀️", "🌙", "💫", "❤️", "💋", "📸", "🌹",
)

@Composable
fun OverlayPanel(
    selected: Overlay?,
    onAddText: (String, Int) -> Unit,
    onAddEmoji: (String) -> Unit,
    onChange: (Overlay) -> Unit,
    onDelete: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    var dialog by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(TEXT_COLORS[0]) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("Add text", Icons.Rounded.TextFields, { text = ""; dialog = true })
            Text("or tap a sticker", color = TextDim, fontSize = 12.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            items(EMOJIS) { e ->
                Text(e, fontSize = 28.sp, modifier = Modifier.clip(CircleShape).clickable { onAddEmoji(e) }.padding(6.dp))
            }
        }
        if (selected != null) {
            LabeledSlider(
                "Size", selected.scale, { onChange(selected.copy(scale = it)) }, range = 0.3f..6f,
                onFinished = onCommit, display = { "%.1f×".format(it) },
            )
            if (!selected.emoji) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    items(TEXT_COLORS) { c ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == selected.color) 3.dp else 1.dp, if (c == selected.color) Pink else Color(0x55FFFFFF), CircleShape)
                                .clickable { onChange(selected.copy(color = c)); onCommit() },
                        )
                    }
                }
            }
            GlowChip("Delete", icon = Icons.Rounded.Delete) { onDelete(selected.id) }
            Text("Drag to move. Pinch to resize and rotate.", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text("Add text or a sticker, then drag it where you want it.", color = TextDim, fontSize = 11.sp)
        }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Add text") },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, placeholder = { Text("Your text") })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                        items(TEXT_COLORS) { c ->
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                    .border(if (c == color) 3.dp else 1.dp, if (c == color) Pink else Color(0x55FFFFFF), CircleShape)
                                    .clickable { color = c },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) onAddText(text.trim(), color)
                    dialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
    }
}
