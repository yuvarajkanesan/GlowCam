package com.glowcam.ui

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glowcam.AppSettings
import com.glowcam.CrashLog
import com.glowcam.camera.LocationTagger

private const val PRIVACY = """GlowCam works entirely on your phone.

• Camera and microphone: used only to show the live preview and to record your photos and videos.
• Face and person detection run on the device. No image, video or face data is uploaded, stored on a server or shared with anyone.
• Photos and videos are saved to Pictures/GlowCam and Movies/GlowCam on your phone. You can delete them at any time from the gallery.
• Location (optional): if you turn on "Location tag", the phone's last known location is written into new photos. It is off by default and never leaves your phone unless you share the photo.
• GlowCam has no accounts, no ads and no analytics. If the app ever crashes, a report is saved on your phone only; you decide whether to share it.

Sharing to WhatsApp, Instagram, Snapchat or any other app happens only when you tap a share button, and only the selected photo or video is passed to that app."""

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(title, color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        Column(Modifier.fillMaxWidth().background(Color(0xFF1B1B21), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, v) in options) GlowChip(name, selected == v) { onSelect(v) }
        }
    }
}

@Composable
private fun LinkRow(label: String, hint: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (hint != null) Text(hint, color = TextDim, fontSize = 11.sp)
    }
}

@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var showPrivacy by remember { mutableStateOf(false) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.any { it }
        settings.locationTag.set(ok)
        if (!ok) Toast.makeText(context, "Location permission is needed to tag photos", Toast.LENGTH_SHORT).show()
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    Column(Modifier.fillMaxSize().background(Ink), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassButton(Icons.Rounded.ArrowBack, "Back", onBack)
            Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            Section("CAMERA") {
                SettingSwitch("Mirror selfie", "Selfies look like your reflection", settings.mirrorSelfie.value) { settings.mirrorSelfie.set(it) }
                SettingSwitch("Shutter sound", null, settings.shutterSound.value) { settings.shutterSound.set(it) }
                SettingSwitch("Volume keys take photos", "Press volume up or down as the shutter", settings.volumeShutter.value) { settings.volumeShutter.set(it) }
                SettingSwitch("Touch to shoot", "Tap the viewfinder to take a photo", settings.touchShoot.value) { settings.touchShoot.set(it) }
                SettingSwitch("Sharp mode", "Waits until you're steady and keeps the sharpest of 3 shots", settings.sharp.value) { settings.sharp.set(it) }
                SettingSwitch("Original quality (rear camera)", "Saves the camera's untouched JPEG when no effects are on", settings.original.value) { settings.original.set(it) }
            }
            Section("PHOTO") {
                ChoiceRow("JPEG quality", listOf("Good 90" to 90, "High 95" to 95, "Fine 97" to 97, "Max 100" to 100), settings.photoQuality.value) { settings.photoQuality.set(it) }
                SettingSwitch(
                    "Location tag", "Adds where the photo was taken (off by default)", settings.locationTag.value,
                ) { on ->
                    if (!on) settings.locationTag.set(false)
                    else if (LocationTagger.hasPermission(context)) settings.locationTag.set(true)
                    else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }
            Section("VIDEO") {
                ChoiceRow("Resolution", listOf("HD 720p" to 720, "Full HD 1080p" to 1080, "4K" to 2160), settings.videoRes.value) { settings.videoRes.set(it) }
                ChoiceRow("Frame rate", listOf("30 fps" to 30, "60 fps" to 60), settings.videoFps.value) { settings.videoFps.set(it) }
                Text(
                    "4K and 60 fps need a lot of power with beauty effects on. If recording looks choppy, use 1080p 30 fps.",
                    color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Section("VIEWFINDER") {
                ChoiceRow("Grid", listOf("Off" to 0, "Thirds" to 1, "Golden" to 2, "Diagonal" to 3), settings.gridStyle.value) { settings.gridStyle.set(it) }
                SettingSwitch("Level indicator", "A horizon line that turns green when the phone is straight", settings.showLevel.value) { settings.showLevel.set(it) }
                SettingSwitch("Histogram", "Live brightness chart", settings.showHistogram.value) { settings.showHistogram.set(it) }
            }
            Section("ABOUT") {
                LinkRow("Privacy policy", "Everything stays on your phone") { showPrivacy = true }
                LinkRow("Share last crash report", if (CrashLog.read(context) != null) "A report is available" else "No crashes recorded") {
                    val text = CrashLog.read(context)
                    if (text == null) Toast.makeText(context, "No crash report. Nice!", Toast.LENGTH_SHORT).show()
                    else context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                            "Share crash report",
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                Text("GlowCam $version", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
            Box(Modifier.padding(top = 8.dp))
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy policy") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(PRIVACY.replace("\\u2022", "•"), fontSize = 13.sp) } },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Close") } },
        )
    }
}
