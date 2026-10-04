package com.glowcam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glowcam.AppSettings
import com.glowcam.camera.CameraCaps
import com.glowcam.camera.ShootMode
import kotlin.math.pow
import kotlin.math.roundToInt

object ProMath {
    fun iso(t: Float, r: IntRange): Int =
        (r.first * (r.last.toFloat() / r.first).pow(t.coerceIn(0f, 1f))).roundToInt().coerceIn(r.first, r.last)

    fun isoT(iso: Int, r: IntRange): Float =
        (kotlin.math.ln(iso.toFloat() / r.first) / kotlin.math.ln(r.last.toFloat() / r.first)).coerceIn(0f, 1f)

    fun ns(t: Float, r: LongRange): Long =
        (r.first * (r.last.toDouble() / r.first).pow(t.coerceIn(0f, 1f).toDouble())).toLong().coerceIn(r.first, r.last)

    fun nsT(ns: Long, r: LongRange): Float =
        (kotlin.math.ln(ns.toDouble() / r.first) / kotlin.math.ln(r.last.toDouble() / r.first)).toFloat().coerceIn(0f, 1f)

    fun shutterLabel(ns: Long): String =
        if (ns >= 500_000_000L) "%.1fs".format(ns / 1e9) else "1/${(1e9 / ns).roundToInt()}"
}

@Composable
fun SettingSwitch(label: String, hint: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) Color.White else TextDim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (hint != null) Text(hint, color = TextDim, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Pink, checkedThumbColor = Color.White),
        )
    }
}

/** The "Capture" sheet: shooting mode, quality options, Pro controls and viewfinder aids. */
@Composable
fun CapturePanel(
    settings: AppSettings,
    caps: CameraCaps,
    front: Boolean,
    shootMode: ShootMode,
    onShootMode: (ShootMode) -> Unit,
    proOn: Boolean,
    onProOn: (Boolean) -> Unit,
    isoT: Float,
    onIsoT: (Float) -> Unit,
    shutterT: Float,
    onShutterT: (Float) -> Unit,
    awb: Int,
    onAwb: (Int) -> Unit,
) {
    Column {
        Text("Shooting mode", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowChip("Auto", shootMode == ShootMode.AUTO) { onShootMode(ShootMode.AUTO) }
            GlowChip("HDR", shootMode == ShootMode.HDR) { if (caps.hdr) onShootMode(ShootMode.HDR) }
            GlowChip("Night", shootMode == ShootMode.NIGHT) { onShootMode(ShootMode.NIGHT) }
        }
        Text(
            when (shootMode) {
                ShootMode.AUTO -> "Everyday shooting."
                ShootMode.HDR -> "Keeps detail in bright skies and dark shadows."
                ShootMode.NIGHT -> "Low light: brighter, longer exposure. Hold very still."
            },
            color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )

        SettingSwitch("Sharp mode", "Image stabilisation, better noise and edge quality, and a hold-steady warning", settings.sharp.value) { settings.sharp.set(it) }
        SettingSwitch(
            "Original quality", if (front) "Rear camera only: saves the camera's untouched JPEG" else "Saves the camera's untouched JPEG when no effects are on",
            settings.original.value, enabled = !front,
        ) { settings.original.set(it) }

        SettingSwitch("Pro mode", if (caps.manual) "Manual ISO, shutter speed and white balance" else "Not available on this camera", proOn, enabled = caps.manual) { onProOn(it) }
        if (proOn && caps.manual) {
            LabeledSlider("ISO", isoT, onIsoT, display = { "${ProMath.iso(it, caps.isoRange)}" })
            LabeledSlider("Shutter", shutterT, onShutterT, display = { ProMath.shutterLabel(ProMath.ns(it, caps.exposureNsRange)) })
            Text("White balance", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowChip("Auto", awb == 1) { onAwb(1) }
                GlowChip("Daylight", awb == 5) { onAwb(5) }
                GlowChip("Cloudy", awb == 6) { onAwb(6) }
                GlowChip("Tungsten", awb == 2) { onAwb(2) }
                GlowChip("Fluorescent", awb == 3) { onAwb(3) }
            }
        }

        Text("Viewfinder aids", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val gridNames = listOf("Grid off", "Thirds", "Golden", "Diagonal")
            GlowChip(gridNames[settings.gridStyle.value], settings.gridStyle.value > 0) { settings.gridStyle.set((settings.gridStyle.value + 1) % 4) }
            GlowChip("Level", settings.showLevel.value) { settings.showLevel.toggle() }
            GlowChip("Histogram", settings.showHistogram.value) { settings.showHistogram.toggle() }
            GlowChip("Touch to shoot", settings.touchShoot.value) { settings.touchShoot.toggle() }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(1.dp).padding(bottom = 6.dp))
    }
}
