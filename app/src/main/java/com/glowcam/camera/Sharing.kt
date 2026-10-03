package com.glowcam.camera

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

enum class ShareTarget(val label: String, val pkg: String?) {
    WHATSAPP("WhatsApp", "com.whatsapp"),
    INSTAGRAM("Instagram", "com.instagram.android"),
    SNAPCHAT("Snapchat", "com.snapchat.android"),
    MORE("More", null),
}

object Sharing {
    /** Shares straight to the target app; falls back to the system share sheet if it is not installed. */
    fun share(context: Context, uri: Uri, isVideo: Boolean, target: ShareTarget) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (target.pkg != null) {
            try {
                context.startActivity(Intent(send).setPackage(target.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "${target.label} is not installed", Toast.LENGTH_SHORT).show()
            }
        }
        val chooser = Intent.createChooser(send, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun view(context: Context, uri: Uri, isVideo: Boolean) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(i) } catch (_: ActivityNotFoundException) {}
    }
}
