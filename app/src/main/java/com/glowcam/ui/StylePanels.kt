package com.glowcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glowcam.gl.EffectParams
import com.glowcam.gl.Look
import com.glowcam.gl.LookStore
import com.glowcam.gl.Looks

private val LIP_COLORS = listOf(0xFFD6567A, 0xFFC62828, 0xFFFF6F61, 0xFFC98B7B, 0xFF8E1B4A, 0xFF6A1B5A, 0xFFFF4D8D, 0xFFE8541E).map { it.toInt() }
private val BLUSH_COLORS = listOf(0xFFFF9E80, 0xFFFF7A9A, 0xFFFF5FA2, 0xFFFF6F61, 0xFFC77DA0).map { it.toInt() }
private val SHADOW_COLORS = listOf(0xFF9C6FD6, 0xFFD4A24C, 0xFF8C5A3C, 0xFFD98FA3, 0xFF4A4A5A, 0xFF2E8B8B).map { it.toInt() }
private val BROW_COLORS = listOf(0xFF1C1512, 0xFF3A2A22, 0xFF6B3A2A).map { it.toInt() }

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        items(colors) { c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) Color.White else Color(0x55FFFFFF), CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

/** Lipstick, blush, eyebrows and eye shadow. */
@Composable
fun MakeupPanel(p: EffectParams, onChange: (EffectParams) -> Unit, onCommit: () -> Unit = {}) {
    Column {
        Text("Lipstick", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        ColorRow(LIP_COLORS, p.lipColor) { onChange(p.copy(lipColor = it, lip = if (p.lip == 0f) 0.55f else p.lip)); onCommit() }
        LabeledSlider("Lipstick", p.lip, { onChange(p.copy(lip = it)) }, onFinished = onCommit)

        Text("Blush", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        ColorRow(BLUSH_COLORS, p.blushColor) { onChange(p.copy(blushColor = it, blush = if (p.blush == 0f) 0.45f else p.blush)); onCommit() }
        LabeledSlider("Blush", p.blush, { onChange(p.copy(blush = it)) }, onFinished = onCommit)

        Text("Eye shadow", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        ColorRow(SHADOW_COLORS, p.shadowColor) { onChange(p.copy(shadowColor = it, eyeShadow = if (p.eyeShadow == 0f) 0.45f else p.eyeShadow)); onCommit() }
        LabeledSlider("Eye shadow", p.eyeShadow, { onChange(p.copy(eyeShadow = it)) }, onFinished = onCommit)

        Text("Eyebrows", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        ColorRow(BROW_COLORS, p.browColor) { onChange(p.copy(browColor = it, brow = if (p.brow == 0f) 0.45f else p.brow)); onCommit() }
        LabeledSlider("Eyebrows", p.brow, { onChange(p.copy(brow = it)) }, onFinished = onCommit)
    }
}

private data class BgOption(val label: String, val mode: Int, val c1: Int, val c2: Int)

private val BG_OPTIONS = listOf(
    BgOption("None", 0, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
    BgOption("Blur", 3, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
    BgOption("White", 1, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
    BgOption("Black", 1, 0xFF0E0E12.toInt(), 0xFF0E0E12.toInt()),
    BgOption("Pink", 1, 0xFFFFC1D9.toInt(), 0xFFFFC1D9.toInt()),
    BgOption("Sky", 1, 0xFF9ED0FF.toInt(), 0xFF9ED0FF.toInt()),
    BgOption("Mint", 1, 0xFFB5EAD7.toInt(), 0xFFB5EAD7.toInt()),
    BgOption("Sunset", 2, 0xFFFF5FA2.toInt(), 0xFFFF8A5B.toInt()),
    BgOption("Ocean", 2, 0xFF2E3192.toInt(), 0xFF1BFFFF.toInt()),
    BgOption("Studio", 2, 0xFF3A3A44.toInt(), 0xFF0E0E12.toInt()),
    BgOption("Gold bokeh", 4, 0xFF5A3010.toInt(), 0xFFFFC866.toInt()),
    BgOption("Night lights", 5, 0xFF0A0C30.toInt(), 0xFF7FB0FF.toInt()),
    BgOption("Pastel bokeh", 6, 0xFFFFD9E8.toInt(), 0xFFE6DEFF.toInt()),
    BgOption("Spotlight", 7, 0xFF1A1A1F.toInt(), 0xFFD0D0D6.toInt()),
)

/** Replace the background behind the person. */
@Composable
fun BackgroundPanel(p: EffectParams, onChange: (EffectParams) -> Unit, onCommit: () -> Unit = {}) {
    Column {
        Text("Keeps you sharp and replaces everything behind you.", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(BG_OPTIONS) { o ->
                val sel = o.mode == p.bgMode && (o.mode == 0 || o.mode >= 3 || (o.c1 == p.bgColor1 && o.c2 == p.bgColor2))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    onChange(p.copy(bgMode = o.mode, bgColor1 = o.c1, bgColor2 = o.c2)); onCommit()
                }) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(
                                when (o.mode) {
                                    2, 4, 5, 6, 7 -> Brush.verticalGradient(listOf(Color(o.c2), Color(o.c1)))
                                    1 -> Brush.verticalGradient(listOf(Color(o.c1), Color(o.c1)))
                                    else -> Brush.verticalGradient(listOf(Color(0xFF2A2A33), Color(0xFF2A2A33)))
                                },
                            )
                            .border(if (sel) 3.dp else 1.dp, if (sel) Pink else Color(0x55FFFFFF), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (o.mode) {
                            0 -> Icon(Icons.Rounded.Block, null, tint = TextDim)
                            3 -> Icon(Icons.Rounded.BlurOn, null, tint = Color.White)
                            else -> {}
                        }
                    }
                    Text(o.label, color = if (sel) Color.White else TextDim, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        if (p.bgMode == 3) LabeledSlider("Blur", p.bgBlur, { onChange(p.copy(bgBlur = it)) }, onFinished = onCommit)
    }
}

/** One-tap styles plus the user's own saved looks. */
@Composable
fun LooksPanel(p: EffectParams, onChange: (EffectParams) -> Unit, onCommit: () -> Unit = {}) {
    val context = LocalContext.current
    var user by remember { mutableStateOf(LookStore.load(context)) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column {
        Text("Tap a look to apply it. You can fine-tune it afterwards.", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(
                    Modifier.size(width = 84.dp, height = 62.dp).clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(14.dp)).clickable { name = ""; naming = true },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White)
                    Text("Save look", color = Color.White, fontSize = 10.sp)
                }
            }
            items(user, key = { it.id }) { u ->
                LookCard(u.name, 0xFF3A3A44.toInt(), 0xFFFF5FA2.toInt(), onDelete = {
                    LookStore.delete(context, u.id); user = LookStore.load(context)
                }) { onChange(LookStore.fromJson(p, u.json)); onCommit() }
            }
            items(Looks.presets, key = { it.id }) { l: Look ->
                LookCard(l.name, l.colorA, l.colorB, null) { onChange(l.apply(p)); onCommit() }
            }
        }
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save this look") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, singleLine = true, placeholder = { Text("Name, e.g. Date night") })
            },
            confirmButton = {
                TextButton(onClick = {
                    LookStore.save(context, name.ifBlank { "My look" }, p)
                    user = LookStore.load(context)
                    naming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LookCard(name: String, a: Int, b: Int, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 92.dp, height = 62.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(a), Color(b)))).clickable(onClick = onClick),
    ) {
        Text(
            name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x66000000)).padding(vertical = 4.dp, horizontal = 4.dp),
        )
        if (onDelete != null) {
            Icon(
                Icons.Rounded.Close, "Delete look", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).background(Color(0x99000000), CircleShape)
                    .clickable(onClick = onDelete).padding(2.dp),
            )
        }
    }
}
