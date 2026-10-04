package com.glowcam.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class Aspect(val label: String, val ratio: Float, val wide: Boolean) {
    R4_3("4:3", 3f / 4f, false),
    R16_9("16:9", 9f / 16f, true),
    R1_1("1:1", 1f, false),
}

enum class Mode(val label: String) { PHOTO("Photo"), VIDEO("Video") }

/** Composition guides: 1 thirds, 2 golden ratio, 3 diagonals. */
@Composable
fun GridOverlay(style: Int, modifier: Modifier = Modifier) {
    if (style == 0) return
    Canvas(modifier) {
        val c = Color(0x66FFFFFF)
        val w = size.width
        val h = size.height
        when (style) {
            1 -> for (i in 1..2) {
                drawLine(c, Offset(w * i / 3, 0f), Offset(w * i / 3, h), 1.5f)
                drawLine(c, Offset(0f, h * i / 3), Offset(w, h * i / 3), 1.5f)
            }
            2 -> for (f in listOf(0.382f, 0.618f)) {
                drawLine(c, Offset(w * f, 0f), Offset(w * f, h), 1.5f)
                drawLine(c, Offset(0f, h * f), Offset(w, h * f), 1.5f)
            }
            3 -> {
                drawLine(c, Offset(0f, 0f), Offset(w, h), 1.5f)
                drawLine(c, Offset(w, 0f), Offset(0f, h), 1.5f)
                drawLine(c, Offset(w / 2, 0f), Offset(w / 2, h), 1.5f)
                drawLine(c, Offset(0f, h / 2), Offset(w, h / 2), 1.5f)
            }
        }
    }
}

/** Horizon line: turns green when the phone is level. */
@Composable
fun LevelOverlay(rollDeg: Float, flat: Boolean, modifier: Modifier = Modifier) {
    if (flat) return
    val level = abs(rollDeg) < 1.2f
    val color by animateColorAsState(if (level) Color(0xFF4CD08B) else Color(0xCCFFFFFF), label = "levelColor")
    Canvas(modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val half = size.width * 0.18f
        // fixed marks
        drawLine(Color(0x88FFFFFF), Offset(cx - half * 1.9f, cy), Offset(cx - half * 1.5f, cy), 3f, StrokeCap.Round)
        drawLine(Color(0x88FFFFFF), Offset(cx + half * 1.5f, cy), Offset(cx + half * 1.9f, cy), 3f, StrokeCap.Round)
        rotate(-rollDeg, Offset(cx, cy)) {
            drawLine(color, Offset(cx - half, cy), Offset(cx + half, cy), 4f, StrokeCap.Round)
        }
    }
}

/** Tiny live brightness chart (64 bins). */
@Composable
fun HistogramOverlay(bins: IntArray, modifier: Modifier = Modifier) {
    val peak = max(1, bins.maxOrNull() ?: 1)
    Canvas(modifier.size(width = 110.dp, height = 46.dp).background(Color(0x66000000), RoundedCornerShape(8.dp)).padding(4.dp)) {
        val bw = size.width / bins.size
        for (i in bins.indices) {
            val h = size.height * (bins[i].toFloat() / peak).coerceIn(0f, 1f)
            drawRect(Color(0xCCFFFFFF), Offset(i * bw, size.height - h), androidx.compose.ui.geometry.Size(bw * 0.8f, h))
        }
    }
}

/** Zoom lens chips (0.6x, 1x, 2x ...), the one closest to the current zoom is highlighted. */
@Composable
fun ZoomChips(zoom: Float, min: Float, max: Float, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    val stops = listOf(0.6f, 1f, 2f, 3f, 5f, 10f).filter { it >= min - 0.01f && it <= max + 0.01f }
    if (stops.size < 2) return
    val active = stops.minByOrNull { abs(it - zoom) }
    Row(
        modifier.background(Glass, CircleShape).padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (s in stops) {
            val sel = s == active
            val label = if (sel && abs(zoom - s) > 0.05f) "%.1f×".format(zoom) else (if (s < 1f) "$s" else "${s.roundToInt()}") + if (sel) "×" else ""
            Text(
                label, color = if (sel) Color.White else TextDim, fontSize = 12.sp,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).background(if (sel) Pink else Color.Transparent)
                    .clickable { onZoom(s) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Vertical brightness slider that appears next to the focus ring. [value] is -1..1. */
@Composable
fun ExposureSlider(value: Float, onChange: (Float) -> Unit, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    val trackH = 150.dp
    Column(modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LightMode, null, tint = Color(0xFFFFD166), modifier = Modifier.size(20.dp))
        Box(
            Modifier.padding(top = 6.dp).size(width = 36.dp, height = trackH)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragStart = { onTouch() }) { change, _ ->
                        change.consume()
                        onTouch()
                        onChange((1f - (change.position.y / size.height) * 2f).coerceIn(-1f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        onTouch()
                        onChange((1f - (off.y / size.height) * 2f).coerceIn(-1f, 1f))
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                drawLine(Color(0x88FFFFFF), Offset(x, 0f), Offset(x, size.height), 3f, StrokeCap.Round)
                val y = size.height * (1f - value) / 2f
                drawCircle(Color(0xFFFFD166), 9.dp.toPx(), Offset(x, y))
            }
        }
    }
}

@Composable
fun ModeSwitch(mode: Mode, enabled: Boolean, onMode: (Mode) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Glass).padding(3.dp)) {
        for (m in Mode.values()) {
            val sel = mode == m
            Row(
                Modifier.clip(CircleShape).background(if (sel) Color(0x40FFFFFF) else Color.Transparent)
                    .clickable(enabled = enabled) { onMode(m) }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (m == Mode.PHOTO) Icons.Rounded.CameraAlt else Icons.Rounded.Videocam, null,
                    tint = if (sel) Color.White else TextDim, modifier = Modifier.size(16.dp),
                )
                Text(
                    "  ${m.label}", color = if (sel) Color.White else TextDim, fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun Shutter(
    video: Boolean, recording: Boolean, progress: Float, busy: Boolean, onClick: () -> Unit,
    onHoldStart: () -> Unit = {}, onHoldEnd: () -> Unit = {},
) {
    // the gesture listener lives across recompositions (busy flips during a burst), so read current values
    val latestBusy = rememberUpdatedState(busy)
    val latestClick = rememberUpdatedState(onClick)
    val latestHoldStart = rememberUpdatedState(onHoldStart)
    val latestHoldEnd = rememberUpdatedState(onHoldEnd)
    val scale by animateFloatAsState(if (busy) 0.9f else 1f, label = "shutterScale")
    val inner by animateDpAsState(if (recording) 30.dp else 62.dp, label = "shutterInner")
    val corner by animateDpAsState(if (recording) 8.dp else 31.dp, label = "shutterCorner")
    Box(
        Modifier.size(84.dp).scale(scale).alpha(if (busy) 0.6f else 1f)
            // tap = one shot; press and hold = keep shooting until released (the hold callbacks)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { tryAwaitRelease(); latestHoldEnd.value() },
                    onLongPress = { latestHoldStart.value() },
                    onTap = { if (!latestBusy.value) latestClick.value() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White, style = Stroke(10f))
            if (recording) {
                drawArc(Danger, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(10f, cap = StrokeCap.Round))
            }
        }
        Box(
            Modifier.size(inner).clip(RoundedCornerShape(corner))
                .background(if (video) Danger else Pink)
                .then(if (!video) Modifier.background(BrandBrush) else Modifier),
        )
    }
}

@Composable
fun PermissionScreen(onAllow: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Ink).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(BrandBrush), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CameraAlt, null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Text("Let's get glowing", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text(
            "GlowCam needs your camera to show a live beauty preview. Everything is processed on your phone; nothing is uploaded.",
            color = TextDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
        GradientButton("Allow camera", Icons.Rounded.CameraAlt, onAllow)
        Text(
            "Open app settings", color = Pink, fontSize = 14.sp,
            modifier = Modifier.padding(top = 18.dp).clickable(onClick = onSettings).padding(8.dp),
        )
    }
}

@Suppress("unused")
private val keepBorder = Modifier.border(0.dp, Color.Transparent)

@Suppress("unused")
private val keepHeight = Modifier.height(0.dp)
