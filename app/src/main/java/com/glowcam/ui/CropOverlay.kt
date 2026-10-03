package com.glowcam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.glowcam.gl.CropRect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Crop rectangle in normalized frame coordinates, origin top-left. */
data class NRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t

    fun toCrop() = CropRect(l, 1f - b, w, h)

    companion object {
        fun from(c: CropRect) = NRect(c.x, 1f - (c.y + c.h), c.x + c.w, 1f - c.y)
    }
}

/** Largest same-aspect axis-aligned rectangle that fits inside the image rotated by [deg]. */
fun inscribedCrop(deg: Float, srcAspect: Float): CropRect {
    val a = Math.toRadians(abs(deg).toDouble())
    val s = (1.0 / (cos(a) + sin(a) * max(srcAspect, 1f / srcAspect))).toFloat().coerceIn(0.2f, 1f)
    return CropRect((1f - s) / 2f, (1f - s) / 2f, s, s)
}

private enum class Drag { NONE, MOVE, TL, TR, BL, BR }

/**
 * Draggable crop box drawn over the displayed image ([imgRect] in pixels).
 * [ratio] = locked crop pixel ratio (w/h) or null for free; [srcAspect] = image width/height.
 */
@Composable
fun CropOverlay(
    rect: NRect,
    imgRect: Rect,
    ratio: Float?,
    srcAspect: Float,
    onChange: (NRect) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cur by rememberUpdatedState(rect)
    val lock by rememberUpdatedState(ratio)
    val img by rememberUpdatedState(imgRect)
    val aspect by rememberUpdatedState(srcAspect)
    var mode = Drag.NONE

    Box(
        modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { p ->
                    val r = cur
                    val ir = img
                    fun px(nx: Float) = ir.left + nx * ir.width
                    fun py(ny: Float) = ir.top + ny * ir.height
                    val hit = 56f
                    fun near(x: Float, y: Float) = abs(p.x - x) < hit && abs(p.y - y) < hit
                    mode = when {
                        near(px(r.l), py(r.t)) -> Drag.TL
                        near(px(r.r), py(r.t)) -> Drag.TR
                        near(px(r.l), py(r.b)) -> Drag.BL
                        near(px(r.r), py(r.b)) -> Drag.BR
                        p.x in px(r.l)..px(r.r) && p.y in py(r.t)..py(r.b) -> Drag.MOVE
                        else -> Drag.NONE
                    }
                },
                onDragEnd = { mode = Drag.NONE; onFinished() },
                onDragCancel = { mode = Drag.NONE; onFinished() },
                onDrag = { change, d ->
                    change.consume()
                    val ir = img
                    val dx = d.x / ir.width
                    val dy = d.y / ir.height
                    var r = cur
                    val minS = 0.08f
                    when (mode) {
                        Drag.MOVE -> {
                            val nl = (r.l + dx).coerceIn(0f, 1f - r.w)
                            val nt = (r.t + dy).coerceIn(0f, 1f - r.h)
                            r = NRect(nl, nt, nl + r.w, nt + r.h)
                        }
                        Drag.NONE -> {}
                        else -> {
                            var l = r.l; var t = r.t; var rr = r.r; var b = r.b
                            if (mode == Drag.TL || mode == Drag.BL) l = (l + dx).coerceIn(0f, rr - minS)
                            if (mode == Drag.TR || mode == Drag.BR) rr = (rr + dx).coerceIn(l + minS, 1f)
                            if (mode == Drag.TL || mode == Drag.TR) t = (t + dy).coerceIn(0f, b - minS)
                            if (mode == Drag.BL || mode == Drag.BR) b = (b + dy).coerceIn(t + minS, 1f)
                            val lk = lock
                            if (lk != null) {
                                // keep the pixel ratio: height follows width, anchored at the opposite corner
                                val w = rr - l
                                var h = w * aspect / lk
                                if (h > 1f) { h = 1f }
                                if (mode == Drag.TL || mode == Drag.TR) t = b - h else b = t + h
                                if (t < 0f) { t = 0f; b = h.coerceAtMost(1f) }
                                if (b > 1f) { b = 1f; t = 1f - h }
                            }
                            r = NRect(l, t, rr, b)
                        }
                    }
                    onChange(r)
                },
            )
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = rect
            val ir = imgRect
            val l = ir.left + r.l * ir.width
            val t = ir.top + r.t * ir.height
            val rr = ir.left + r.r * ir.width
            val b = ir.top + r.b * ir.height
            val dim = Color(0x99000000)
            drawRect(dim, Offset(ir.left, ir.top), Size(ir.width, t - ir.top))
            drawRect(dim, Offset(ir.left, b), Size(ir.width, ir.bottom - b))
            drawRect(dim, Offset(ir.left, t), Size(l - ir.left, b - t))
            drawRect(dim, Offset(rr, t), Size(ir.right - rr, b - t))
            drawRect(Color.White, Offset(l, t), Size(rr - l, b - t), style = Stroke(3f))
            val g = Color(0x80FFFFFF)
            for (i in 1..2) {
                drawLine(g, Offset(l + (rr - l) * i / 3, t), Offset(l + (rr - l) * i / 3, b), 1.5f)
                drawLine(g, Offset(l, t + (b - t) * i / 3), Offset(rr, t + (b - t) * i / 3), 1.5f)
            }
            for ((cx, cy) in listOf(l to t, rr to t, l to b, rr to b)) {
                drawCircle(Pink, 14f, Offset(cx, cy))
                drawCircle(Color.White, 14f, Offset(cx, cy), style = Stroke(3f))
            }
        }
    }
}

fun fitRect(boxW: Float, boxH: Float, bmpAspect: Float): Rect {
    val boxAspect = boxW / boxH
    return if (boxAspect > bmpAspect) {
        val w = boxH * bmpAspect
        Rect((boxW - w) / 2f, 0f, (boxW + w) / 2f, boxH)
    } else {
        val h = boxW / bmpAspect
        Rect(0f, (boxH - h) / 2f, boxW, (boxH + h) / 2f)
    }
}

@Suppress("unused")
private fun clamp01(v: Float) = min(1f, max(0f, v))
