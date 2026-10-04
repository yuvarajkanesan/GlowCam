package com.glowcam.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val tips = listOf(
    "Beautiful selfies and photos" to "GlowCam smooths skin, brightens and shapes your face live while you shoot.",
    "Pick a look" to "Swipe through looks and filters on the camera, then adjust the strength to taste.",
    "Capture" to "Tap for a photo, hold the shutter for a burst, or switch to video. Volume keys work as the shutter too.",
    "Edit and share" to "Open any photo to retouch it, add text and stickers, make a collage, then share.",
    "Private by design" to "Everything stays on your phone. Photos are saved to your gallery.",
)

/** First-run walkthrough explaining what the app is for. */
@Composable
fun WelcomeTips(onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Welcome to GlowCam") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tips.forEachIndexed { i, (head, body) ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Text(head, color = Pink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(body, color = TextDim, fontSize = 13.sp, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Got it") } },
    )
}
