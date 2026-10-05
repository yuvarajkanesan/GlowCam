package com.glowcam.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaSaver {
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Android 9 and older need the storage permission to save into the shared Pictures / Movies folders. */
    fun hasStorageAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /**
     * MediaStore values for a new GlowCam file. Android 10+ uses the relative folder; older versions need the
     * real file path. [pending] hides the file until it is complete (Android 10+).
     */
    fun newValues(name: String, mime: String, video: Boolean, pending: Boolean): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        val folder = if (video) "Movies" else "Pictures"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/GlowCam")
            if (pending) put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else {
            @Suppress("DEPRECATION")
            val base = Environment.getExternalStoragePublicDirectory(if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
            val dir = File(base, "GlowCam").apply { mkdirs() }
            @Suppress("DEPRECATION")
            put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
        }
    }

    fun imageValues(pending: Boolean) = newValues("GlowCam_${stamp()}.jpg", "image/jpeg", video = false, pending = pending)

    /** Saves [bmp] as a high-quality JPEG into Pictures/GlowCam and returns its content Uri. */
    fun saveJpeg(context: Context, bmp: Bitmap, quality: Int = 97): Uri {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues(pending = true))
            ?: error("Could not create media entry")
        resolver.openOutputStream(uri)!!.use { out ->
            check(bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encode failed" }
        }
        finish(context, uri)
        return uri
    }

    /** Saves [bmp] as a lossless PNG into Pictures/GlowCam and returns its content Uri. */
    fun savePng(context: Context, bmp: Bitmap): Uri {
        val resolver = context.contentResolver
        val values = newValues("GlowCam_${stamp()}.png", "image/png", video = false, pending = true)
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create media entry")
        resolver.openOutputStream(uri)!!.use { out ->
            check(bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed" }
        }
        finish(context, uri)
        return uri
    }

    /** Creates a pending video entry in Movies/GlowCam. Call [finishVideo] when the file is complete. */
    fun createVideo(context: Context): Uri =
        context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            newValues("GlowCam_${stamp()}.mp4", "video/mp4", video = true, pending = true),
        ) ?: error("Could not create video entry")

    fun finishVideo(context: Context, uri: Uri) = finish(context, uri)

    private fun finish(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }

    /** Absolute file path of a MediaStore item (Android 9 and older, where MediaMuxer cannot take a descriptor). */
    @Suppress("DEPRECATION")
    fun filePath(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun delete(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}
