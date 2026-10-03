package com.glowcam.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.roundToInt

object ImageLoad {
    /** Decodes [uri] upright (EXIF applied), software-backed so it can be uploaded to GL. */
    fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val s = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val rot = context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
            if (raw != null && rot != 0) {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
            } else raw
        }
    } catch (e: Exception) {
        null
    }

    fun videoFrame(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(context, uri)
        val f = r.getFrameAtTime(0)
        r.release()
        f?.let {
            val longest = max(it.width, it.height)
            if (longest > maxSide) {
                val s = maxSide.toFloat() / longest
                Bitmap.createScaledBitmap(it, (it.width * s).roundToInt(), (it.height * s).roundToInt(), true)
            } else it
        }
    } catch (e: Exception) {
        null
    }
}
