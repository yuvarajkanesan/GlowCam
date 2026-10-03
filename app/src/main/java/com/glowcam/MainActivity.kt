package com.glowcam

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.glowcam.ui.CameraScreen
import com.glowcam.ui.CollageScreen
import com.glowcam.ui.EditorScreen
import com.glowcam.ui.GalleryScreen
import com.glowcam.ui.GlowTheme
import com.glowcam.ui.loadGalleryItems
import com.glowcam.ui.ResultScreen
import com.glowcam.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /** Image handed to us by another app ("Edit with GlowCam" / Share). */
    private var incoming by mutableStateOf<Uri?>(null)
    private var volumeTick by mutableIntStateOf(0)
    private lateinit var settings: AppSettings

    @Volatile private var cameraVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        settings = AppSettings(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            GlowTheme {
                GlowCamApp(
                    settings = settings,
                    incoming = incoming,
                    onConsumed = { incoming = null },
                    volumeTick = volumeTick,
                    onCameraVisible = { cameraVisible = it },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Volume keys work as the shutter while the camera is showing. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) &&
            cameraVisible && settings.volumeShutter.value
        ) {
            if (event.repeatCount == 0) volumeTick++
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleIntent(intent: Intent?) {
        val i = intent ?: return
        val candidate = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        // Another app can only hand us a content:// image; file:// and anything else is ignored.
        incoming = candidate?.takeIf { it.scheme == "content" }
    }
}

private sealed interface Screen {
    data object Camera : Screen
    data object Gallery : Screen
    data object Settings : Screen
    data object Collage : Screen
    data class Editor(val uri: Uri) : Screen
    data class Result(val uri: Uri, val isVideo: Boolean) : Screen
}

@Composable
fun GlowCamApp(
    settings: AppSettings,
    incoming: Uri?,
    onConsumed: () -> Unit,
    volumeTick: Int,
    onCameraVisible: (Boolean) -> Unit,
) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Camera) }
    var lastMedia by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    var galleryRefresh by remember { mutableIntStateOf(0) }
    val screen = stack.last()
    val context = LocalContext.current

    // The thumbnail next to the shutter always shows the newest GlowCam photo or video,
    // also after a restart, and falls back to the next newest when one is deleted.
    LaunchedEffect(galleryRefresh) {
        val latest = withContext(Dispatchers.IO) { loadGalleryItems(context).firstOrNull() }
        if (latest != null) lastMedia = latest.uri to latest.isVideo
    }

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(s: Screen) { stack[stack.lastIndex] = s }

    LaunchedEffect(incoming) {
        if (incoming != null) {
            push(Screen.Editor(incoming))
            onConsumed()
        }
    }
    DisposableEffect(screen) {
        onCameraVisible(screen == Screen.Camera)
        onDispose { onCameraVisible(false) }
    }
    if (stack.size > 1) BackHandler { pop() }

    when (val s = screen) {
        Screen.Camera -> CameraScreen(
            settings = settings,
            last = lastMedia,
            onLastChanged = { lastMedia = it; galleryRefresh++ },
            onOpenGallery = { push(Screen.Gallery) },
            onOpenSettings = { push(Screen.Settings) },
            onImport = { push(Screen.Editor(it)) },
            onCollage = { push(Screen.Collage) },
            shutterTrigger = volumeTick,
        )
        Screen.Gallery -> GalleryScreen(
            refreshKey = galleryRefresh,
            onBack = { pop() },
            onOpen = { uri, video -> push(Screen.Result(uri, video)) },
            onDeleted = { gone -> if (lastMedia?.first == gone) lastMedia = null },
        )
        Screen.Settings -> SettingsScreen(settings, onBack = { pop() })
        Screen.Collage -> CollageScreen(
            onBack = { pop() },
            onSaved = {
                lastMedia = it to false
                galleryRefresh++
                replaceTop(Screen.Result(it, false))
            },
        )
        is Screen.Result -> ResultScreen(
            s.uri, s.isVideo,
            onBack = { pop() },
            onEdit = { push(Screen.Editor(it)) },
            onDeleted = { gone ->
                if (lastMedia?.first == gone) lastMedia = null
                galleryRefresh++
                pop()
            },
        )
        is Screen.Editor -> EditorScreen(
            s.uri,
            onClose = { pop() },
            onSaved = {
                lastMedia = it to false
                galleryRefresh++
                replaceTop(Screen.Result(it, false))
            },
        )
    }
}
