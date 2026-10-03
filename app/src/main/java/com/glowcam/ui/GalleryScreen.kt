package com.glowcam.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaItem(val uri: Uri, val isVideo: Boolean, val dateAdded: Long)

/** Everything GlowCam has saved (Pictures/GlowCam and Movies/GlowCam), newest first. */
fun loadGalleryItems(context: Context): List<MediaItem> {
    val out = ArrayList<MediaItem>()
    fun query(collection: Uri, folder: String, isVideo: Boolean) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("$folder/GlowCam%")
        else "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/GlowCam/%")
        try {
            context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (c.moveToNext()) {
                    out += MediaItem(ContentUris.withAppendedId(collection, c.getLong(idCol)), isVideo, c.getLong(dateCol))
                }
            }
        } catch (_: Exception) {
        }
    }
    query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures", false)
    query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies", true)
    return out.sortedByDescending { it.dateAdded }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(refreshKey: Int, onBack: () -> Unit, onOpen: (Uri, Boolean) -> Unit, onDeleted: (Uri) -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val selected = remember { emptyList<Uri>().toMutableStateList() }
    var showDelete by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey, reload) {
        items = withContext(Dispatchers.IO) { loadGalleryItems(context) }
        loaded = true
    }
    val selecting = selected.isNotEmpty()

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selecting) {
                GlassButton(Icons.Rounded.Close, "Cancel selection", { selected.clear() })
                Text("${selected.size} selected", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GlassButton(Icons.Rounded.SelectAll, "Select all", { selected.clear(); selected.addAll(items.map { it.uri }) })
                GlassButton(Icons.Rounded.Share, "Share", { shareMany(context, items.filter { it.uri in selected }) })
                GlassButton(Icons.Rounded.Delete, "Delete", { showDelete = true })
            } else {
                GlassButton(Icons.Rounded.ArrowBack, "Back", onBack)
                Text("Gallery", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) Text("${items.size} items", color = TextDim, fontSize = 13.sp)
            }
        }

        if (loaded && items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PhotoLibrary, null, tint = TextDim, modifier = Modifier.size(40.dp))
                }
                Text("No photos yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Your GlowCam photos and videos will appear here.", color = TextDim, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                GradientButton("Open camera", null, onBack)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items, key = { it.uri.toString() }) { item ->
                    val bmp = rememberBitmap(item.uri, item.isVideo, 320)
                    val isSel = item.uri in selected
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B1B21))
                            .combinedClickable(
                                onClick = {
                                    if (selecting) { if (isSel) selected.remove(item.uri) else selected.add(item.uri) }
                                    else onOpen(item.uri, item.isVideo)
                                },
                                onLongClick = { if (!isSel) selected.add(item.uri) },
                            ),
                    ) {
                        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        if (item.isVideo) {
                            Icon(
                                Icons.Rounded.PlayArrow, null, tint = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(22.dp).background(Color(0x99000000), CircleShape),
                            )
                        }
                        if (selecting) {
                            Box(
                                Modifier.fillMaxSize().background(if (isSel) Color(0x55FF5FA2) else Color.Transparent),
                            )
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                                    .background(if (isSel) Pink else Color(0x66000000)),
                                contentAlignment = Alignment.Center,
                            ) { if (isSel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete ${selected.size} item${if (selected.size == 1) "" else "s"}?") },
            text = { Text("They will be removed from your gallery. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    var n = 0
                    for (u in selected.toList()) {
                        if (try { context.contentResolver.delete(u, null, null) > 0 } catch (e: SecurityException) { false }) {
                            n++; onDeleted(u)
                        }
                    }
                    selected.clear()
                    reload++
                    Toast.makeText(context, if (n > 0) "Deleted $n" else "Couldn't delete", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun shareMany(context: Context, picked: List<MediaItem>) {
    if (picked.isEmpty()) return
    val uris = ArrayList(picked.map { it.uri })
    val allVideo = picked.all { it.isVideo }
    val allImage = picked.none { it.isVideo }
    val i = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = when {
            allVideo -> "video/*"
            allImage -> "image/*"
            else -> "*/*"
        }
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
