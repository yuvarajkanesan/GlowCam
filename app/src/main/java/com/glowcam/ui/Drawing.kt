package com.glowcam.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One finger-drawn line. Points are 0..1 in the final image (top-left origin); width is a fraction of the image width. */
data class Stroke(val points: List<Offset>, val color: Int, val width: Float)

private val BRUSH_COLORS = listOf(0xFFFFFFFF, 0xFF111111, 0xFFFF5FA2, 0xFFFFD166, 0xFF7AD3C1, 0xFF6CA8E8, 0xFFFF6F61).map { it.toInt() }

/** Shows the strokes over the photo and, when [interactive], records new ones. */
@Composable
fun DrawLayer(
    strokes: List<Stroke>,
    imgRect: Rect,
    interactive: Boolean,
    color: Int,
    width: Float,
    onStroke: (Stroke) -> Unit,
) {
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val curColor by rememberUpdatedState(color)
    val curWidth by rememberUpdatedState(width)
    fun norm(o: Offset) = Offset(
        ((o.x - imgRect.left) / imgRect.width).coerceIn(0f, 1f),
        ((o.y - imgRect.top) / imgRect.height).coerceIn(0f, 1f),
    )
    Canvas(
        Modifier.fillMaxSize().then(
            if (interactive) Modifier.pointerInput(imgRect) {
                detectDragGestures(
                    onDragStart = { live = listOf(norm(it)) },
                    onDrag = { change, _ -> change.consume(); live = live + norm(change.position) },
                    onDragEnd = {
                        if (live.isNotEmpty()) onStroke(Stroke(live, curColor, curWidth))
                        live = emptyList()
                    },
                    onDragCancel = { live = emptyList() },
                )
            } else Modifier,
        ),
    ) {
        fun draw(s: Stroke) {
            val px = s.width * imgRect.width
            val pts = s.points.map { Offset(imgRect.left + it.x * imgRect.width, imgRect.top + it.y * imgRect.height) }
            if (pts.size == 1) {
                drawCircle(Color(s.color), px / 2f, pts[0])
                return
            }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            drawPath(path, Color(s.color), style = DrawStroke(px, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        strokes.forEach { draw(it) }
        if (live.isNotEmpty()) draw(Stroke(live, curColor, curWidth))
    }
}

/** Burns the strokes into a copy of [src] so exported photos match the preview. */
fun drawStrokes(src: Bitmap, strokes: List<Stroke>): Bitmap {
    if (strokes.isEmpty()) return src
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(out)
    for (s in strokes) {
        val w = s.width * out.width
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.color
            strokeWidth = w
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (s.points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(s.points[0].x * out.width, s.points[0].y * out.height, w / 2f, paint)
            continue
        }
        val path = AndroidPath()
        s.points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x * out.width, p.y * out.height) else path.lineTo(p.x * out.width, p.y * out.height)
        }
        canvas.drawPath(path, paint)
    }
    return out
}

@Composable
fun DrawPanel(
    color: Int,
    width: Float,
    hasStrokes: Boolean,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(BRUSH_COLORS) { c ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == color) 3.dp else 1.dp, if (c == color) Pink else Color(0x55FFFFFF), CircleShape)
                        .clickable { onColor(c) },
                )
            }
        }
        LabeledSlider("Brush size", width, onWidth, range = 0.004f..0.06f, display = { "%.0f".format(it * 1000) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (hasStrokes) GlowChip("Clear drawing", icon = Icons.Rounded.Delete, onClick = onClear)
        }
        Text("Draw with your finger. Undo removes the last line.", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
