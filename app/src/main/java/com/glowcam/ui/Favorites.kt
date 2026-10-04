package com.glowcam.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf

/** Hearted photos and videos, remembered on the device. */
object Favorites {
    private const val PREFS = "glowcam_favorites"
    private const val KEY = "uris"
    private val version = mutableStateOf(0)

    private fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

    fun isFavorite(context: Context, uri: Uri): Boolean {
        version.value // read so composables refresh after a toggle
        return uri.toString() in load(context)
    }

    fun toggle(context: Context, uri: Uri) {
        val next = load(context).toMutableSet()
        if (!next.remove(uri.toString())) next.add(uri.toString())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, next).apply()
        version.value++
    }
}
