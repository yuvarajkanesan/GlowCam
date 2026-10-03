package com.glowcam.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.glowcam.camera.MediaSaver
import com.glowcam.camera.ShareTarget
import com.glowcam.camera.Sharing
import java.text.DateFormat
import java.util.Date

@Composable
fun ResultScreen(
    uri: Uri,
    isVideo: Boolean,
    onBack: () -> Unit,
    onEdit: (Uri) -> Unit,
    onDeleted: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val bmp = rememberBitmap(uri, isVideo, 1600)
    var menu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Ink), horizontalAlignment = Alignment.CenterHorizontally) {
        // ---------- top bar ----------
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassButton(Icons.Rounded.ArrowBack, "Back", onBack)
            Box(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CD08B), modifier = Modifier.size(16.dp))
                Text("  Saved", color = TextDim, fontSize = 13.sp)
            }
            Box(Modifier.weight(1f))
            Box {
                GlassButton(Icons.Rounded.MoreVert, "More options", { menu = true })
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = Color(0xFF23232B),
                ) {
                    for (t in ShareTarget.values().filter { it.pkg != null }) {
                        DropdownMenuItem(
                            text = { Text("Share to ${t.label}", color = Color.White) },
                            leadingIcon = { AppIcon(t.pkg!!, Modifier.size(24.dp)) },
                            onClick = { menu = false; Sharing.share(context, uri, isVideo, t) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("More apps…", color = Color.White) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null, tint = Color.White) },
                        onClick = { menu = false; Sharing.share(context, uri, isVideo, ShareTarget.MORE) },
                    )
                    HorizontalDivider(color = Color(0x33FFFFFF))
                    DropdownMenuItem(
                        text = { Text("Open in Gallery", color = Color.White) },
                        leadingIcon = { Icon(Icons.Rounded.OpenInNew, null, tint = Color.White) },
                        onClick = { menu = false; Sharing.view(context, uri, isVideo) },
                    )
                    if (!isVideo) {
                        DropdownMenuItem(
                            text = { Text("Set as wallpaper", color = Color.White) },
                            leadingIcon = { Icon(Icons.Rounded.Wallpaper, null, tint = Color.White) },
                            onClick = { menu = false; setAsWallpaper(context, uri) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Details", color = Color.White) },
                        leadingIcon = { Icon(Icons.Rounded.Info, null, tint = Color.White) },
                        onClick = { menu = false; showDetails = true },
                    )
                    HorizontalDivider(color = Color(0x33FFFFFF))
                    DropdownMenuItem(
                        text = { Text("Delete", color = Danger) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Danger) },
                        onClick = { menu = false; showDelete = true },
                    )
                }
            }
        }

        // ---------- preview ----------
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B1B21))) {
                bmp?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (isVideo) {
                    Box(
                        Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(GlassStrong)
                            .clickable { Sharing.view(context, uri, true) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
            }
        }

        // ---------- action bar ----------
        Row(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionButton(Icons.Rounded.Share, "Share") { Sharing.share(context, uri, isVideo, ShareTarget.MORE) }
            if (!isVideo) ActionButton(Icons.Rounded.Edit, "Edit") { onEdit(uri) }
            ActionButton(Icons.Rounded.Delete, "Delete", tint = Danger) { showDelete = true }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(if (isVideo) "Delete this video?" else "Delete this photo?") },
            text = { Text("It will be removed from your gallery. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    if (deleteMedia(context, uri)) {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        onDeleted(uri)
                    } else {
                        Toast.makeText(context, "Couldn't delete this file", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
    if (showDetails) {
        val rows = remember(uri) { queryDetails(context, uri) }
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, v) in rows) {
                        Column {
                            Text(k, fontSize = 11.sp, color = TextDim)
                            Text(v, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Text(label, color = if (tint == Color.White) Color.White else tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Shows an installed app's own launcher icon (WhatsApp, Instagram, ...). */
@Composable
private fun AppIcon(pkg: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(pkg) { runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull() }
    if (icon != null) Image(icon.asImageBitmap(), null, modifier.clip(RoundedCornerShape(6.dp)))
    else Icon(Icons.Rounded.Share, null, tint = Color.White, modifier = modifier)
}

private fun deleteMedia(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.delete(uri, null, null) > 0
} catch (e: SecurityException) {
    false
}

private fun setAsWallpaper(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(uri, "image/*")
        putExtra("mimeType", "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Set as").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to set the wallpaper", Toast.LENGTH_SHORT).show()
    }
}

private fun queryDetails(context: Context, uri: Uri): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val projection = arrayOf(
        MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DURATION,
    )
    try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                fun s(col: String): String? = c.getColumnIndex(col).takeIf { it >= 0 }?.let { if (c.isNull(it)) null else c.getString(it) }
                s(MediaStore.MediaColumns.DISPLAY_NAME)?.let { out += "Name" to it }
                s(MediaStore.MediaColumns.RELATIVE_PATH)?.let { out += "Location" to it }
                s(MediaStore.MediaColumns.SIZE)?.toLongOrNull()?.let { out += "Size" to Formatter.formatShortFileSize(context, it) }
                val w = s(MediaStore.MediaColumns.WIDTH)?.toIntOrNull()
                val h = s(MediaStore.MediaColumns.HEIGHT)?.toIntOrNull()
                if (w != null && h != null && w > 0) out += "Resolution" to "$w × $h"
                s(MediaStore.MediaColumns.DURATION)?.toLongOrNull()?.takeIf { it > 0 }?.let {
                    out += "Duration" to "%d:%02d".format(it / 60000, (it / 1000) % 60)
                }
                s(MediaStore.MediaColumns.MIME_TYPE)?.let { out += "Type" to it }
                s(MediaStore.MediaColumns.DATE_ADDED)?.toLongOrNull()?.let {
                    out += "Date" to DateFormat.getDateTimeInstance().format(Date(it * 1000))
                }
            }
        }
    } catch (_: Exception) {
    }
    if (out.isEmpty()) out += "File" to uri.toString()
    return out
}

@Suppress("unused")
private val keepImport = MediaSaver
