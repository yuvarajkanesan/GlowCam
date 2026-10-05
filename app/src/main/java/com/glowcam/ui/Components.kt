@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.glowcam.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glowcam.gl.FilterDef
import com.glowcam.gl.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Round translucent icon button used on top of the viewfinder. */
@Composable
fun GlassButton(
    icon: ImageVector,
    description: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    badge: String? = null,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val bg by animateColorAsState(if (selected) Pink else Glass, label = "glassBg")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            // a null onClick leaves touches free for a parent's own gesture (e.g. press and hold)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(size * 0.5f))
        if (badge != null) {
            Text(
                badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 3.dp)
                    .background(Coral, CircleShape).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** Icon above a label; the selected tab gets the brand gradient. */
@Composable
fun ToolTab(icon: ImageVector, label: String, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).then(if (selected) Modifier.background(BrandBrush) else Modifier.background(Color(0x22FFFFFF))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Text(label, color = if (selected) Color.White else TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, softWrap = false)
    }
}

@Composable
fun GlowChip(text: String, selected: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Pink else Color(0x33FFFFFF), label = "chipBg")
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Box(Modifier.width(5.dp))
        }
        Text(text, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun GradientButton(text: String, icon: ImageVector? = null, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(BrandBrush).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Box(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onFinished: (() -> Unit)? = null,
    display: (Float) -> String = { (it * 100).roundToInt().toString() },
) {
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Pink,
        inactiveTrackColor = Color(0x33FFFFFF),
    )
    Row(modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(92.dp), maxLines = 1)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = colors,
            thumb = {
                Box(Modifier.size(22.dp).background(Color.White, CircleShape).border(4.dp, Pink, CircleShape))
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp,
                    drawStopIndicator = null,
                    modifier = Modifier.height(5.dp),
                )
            },
        )
        Text(
            display(value), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(38.dp).background(Color(0x22FFFFFF), RoundedCornerShape(10.dp)).padding(vertical = 3.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Filter strip with category chips and an intensity slider. [thumbs] optionally maps a filter id to a
 * rendered preview; otherwise a colour swatch is shown.
 */
@Composable
fun FilterPicker(
    selected: FilterDef,
    intensity: Float,
    onSelect: (FilterDef) -> Unit,
    onIntensity: (Float) -> Unit,
    onFinished: (() -> Unit)? = null,
    thumbs: Map<String, Bitmap> = emptyMap(),
) {
    val categories = remember { listOf("All") + Filters.all.map { it.category }.distinct().filter { it != "Basic" } }
    var cat by remember { mutableStateOf("All") }
    val shown = remember(cat) {
        Filters.all.filter { cat == "All" || it.category == cat || it.id == "original" }
    }
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            items(categories) { c -> GlowChip(c, cat == c) { cat = c } }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { f ->
                val sel = f.id == selected.id
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.size(width = 72.dp, height = 96.dp).clip(shape)
                        .background(Color(f.swatch()))
                        .border(if (sel) BorderStroke(3.dp, Pink) else BorderStroke(1.dp, Color(0x44FFFFFF)), shape)
                        .clickable {
                            onSelect(f)
                            onFinished?.invoke()
                        },
                ) {
                    val th = thumbs[f.id]
                    if (th != null) {
                        Image(th.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                            .padding(top = 16.dp, bottom = 5.dp, start = 2.dp, end = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            f.name, color = Color.White, fontSize = 10.sp, maxLines = 2, lineHeight = 11.sp, textAlign = TextAlign.Center,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                    if (f.premium) {
                        Icon(
                            Icons.Rounded.Star, null, tint = Color(0xFFFFD166),
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp)
                                .background(Color(0x99000000), CircleShape).padding(2.dp),
                        )
                    }
                    if (sel) {
                        Icon(
                            Icons.Rounded.Check, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                                .background(Pink, CircleShape).padding(2.dp),
                        )
                    }
                }
            }
        }
        if (selected.id != "original") {
            Box(Modifier.height(6.dp))
            LabeledSlider("Intensity", intensity, onIntensity, onFinished = onFinished)
        }
    }
}

/** Loads a thumbnail / preview bitmap off the main thread and remembers it. */
@Composable
fun rememberBitmap(uri: android.net.Uri?, isVideo: Boolean, maxSide: Int): Bitmap? {
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = if (uri == null) null else withContext(Dispatchers.IO) {
            if (isVideo) ImageLoad.videoFrame(context, uri, maxSide) else ImageLoad.loadBitmap(context, uri, maxSide)
        }
    }
    return bmp
}

/** Rounded sheet container used for the tool panels. */
fun Modifier.sheet(): Modifier =
    this.clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(SheetBg)

@Composable
fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(4.dp).background(Color(0x55FFFFFF), CircleShape))
    }
}

/** Round top-bar button that shows the photo size, e.g. "12" over "MP". */
@Composable
fun MegapixelButton(mp: Int, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, size: Dp = 48.dp) {
    val bg by animateColorAsState(if (selected) Pink else Glass, label = "mpBg")
    Column(
        modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(mp.toString(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
        Text("MP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 10.sp)
    }
}

/**
 * Leaving with unsaved work: asks first. Returns a function to call instead of leaving directly;
 * it leaves at once when there is nothing to lose. Also catches the system back button.
 */
@Composable
fun rememberLeaveGuard(hasChanges: Boolean, onLeave: () -> Unit): () -> Unit {
    var ask by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = hasChanges) { ask = true }
    if (ask) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes haven't been saved and will be lost if you leave now.", fontSize = 14.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { ask = false; onLeave() }) { Text("Discard") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ask = false }) { Text("Keep editing") }
            },
        )
    }
    return { if (hasChanges) ask = true else onLeave() }
}
