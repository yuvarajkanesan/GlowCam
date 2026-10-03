package com.glowcam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Pink = Color(0xFFFF5FA2)
val Coral = Color(0xFFFF8A5B)
val Peach = Color(0xFFFFC371)
val Ink = Color(0xFF0E0E12)
val SheetBg = Color(0xE6151519)
val Glass = Color(0x66000000)
val GlassStrong = Color(0x99000000)
val TextDim = Color(0xFFB8B8C2)
val Danger = Color(0xFFE53935)

val BrandBrush = Brush.linearGradient(listOf(Pink, Coral))
val ScrimTop = Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
val ScrimBottom = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))

@Composable
fun GlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pink,
            secondary = Coral,
            background = Ink,
            surface = Color(0xFF1B1B21),
            onSurface = Color.White,
        ),
        content = content,
    )
}
