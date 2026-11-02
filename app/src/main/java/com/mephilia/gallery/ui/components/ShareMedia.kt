package com.mephilia.gallery.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.mephilia.gallery.R
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.ShareCopies
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** MIME type for any image, used when sharing several photos or a converted copy. */
private const val ANY_IMAGE = "image/*"

/** MIME type for any video, used when sharing several videos. */
private const val ANY_VIDEO = "video/*"

/** MIME type for anything, used when sharing photos and videos together. */
private const val ANY_TYPE = "*/*"

/**
 * Preparing copies without location must finish even if the screen that started it goes away,
 * so it runs in a scope that lives as long as the app.
 */
private val shareScope = MainScope()

/**
 * Opens the share sheet for [items].
 *
 * When "remove location when sharing" is on, photos are shared as copies without their GPS data. Photos that
 * couldn't be cleaned are left out rather than shared with their location, and a message says how many.
 */
fun shareMedia(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    if (!AppSettings.current.stripLocationOnShare || items.all { it.isVideo }) {
        startShare(context, items, items.map { it.uri })
        return
    }
    shareScope.launch {
        val uris = ShareCopies.prepare(context.applicationContext, items)
        val kept = items.indices.filter { uris[it] != null }
        val skipped = items.size - kept.size
        if (skipped > 0) {
            Toast.makeText(
                context,
                context.resources.getQuantityString(R.plurals.share_location_failed, skipped, skipped),
                Toast.LENGTH_LONG,
            ).show()
        }
        if (kept.isNotEmpty()) startShare(context, kept.map { items[it] }, kept.mapNotNull { uris[it] })
    }
}

/**
 * Opens the system share sheet with [uris], the content to send for each of [items] (the original,
 * or a copy without location). A converted photo copy is a JPEG, so its type is given as any image.
 */
private fun startShare(context: Context, items: List<MediaItem>, uris: List<Uri>) {
    val intent = if (items.size == 1) {
        val item = items.single()
        val uri = uris.single()
        Intent(Intent.ACTION_SEND).apply {
            type = if (uri != item.uri && !item.isVideo) ANY_IMAGE else item.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = when {
                items.none { it.isVideo } -> ANY_IMAGE
                items.all { it.isVideo } -> ANY_VIDEO
                else -> ANY_TYPE
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}
