package com.mephilia.gallery.ui.components

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** The user declined Android's permission request. */
class AccessDeniedException : CancellationException("Access denied")

/**
 * Modifies media owned by other apps. Acts directly when allowed (media management permission
 * on Android 12+, our own files, or a write grant from the calling app), otherwise falls back to
 * Android's confirmation dialog.
 */
class MediaAccess internal constructor(private val context: Context) {
    /** Shows one of Android's confirmation dialogs; set by [rememberMediaAccess]. */
    internal var launch: (IntentSenderRequest) -> Unit = {}

    /** Receives the user's answer to the dialog being shown, if any. */
    internal var pending: ((Boolean) -> Unit)? = null

    /** Whether the user gave the media management permission (Android 12+), which allows acting without asking. */
    val canManageMedia: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    /** Shows Android's confirmation dialog for [pi] and returns whether the user accepted. */
    private suspend fun ask(pi: PendingIntent): Boolean = suspendCancellableCoroutine { cont ->
        pending = { granted -> if (cont.isActive) cont.resume(granted) }
        launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    /**
     * Runs [block] with write access to [uris], throwing [AccessDeniedException] if the user declines.
     * [block] is tried directly first and may therefore run twice: it must fail before writing anything.
     */
    suspend fun write(uris: List<Uri>, block: suspend () -> Unit) {
        try {
            return block()
        } catch (_: SecurityException) {
        }
        if (!ask(MediaStore.createWriteRequest(context.contentResolver, uris))) throw AccessDeniedException()
        block()
    }

    /** Moves items to the trash. Returns false if the user declines. */
    suspend fun trash(uris: List<Uri>): Boolean {
        if (canManageMedia && direct { uris.forEach { update(it, MediaStore.MediaColumns.IS_TRASHED, 1) } }) return true
        return ask(MediaStore.createTrashRequest(context.contentResolver, uris, true))
    }

    /** Takes items out of the trash. Returns false if the user declines. */
    suspend fun restore(uris: List<Uri>): Boolean {
        if (direct { uris.forEach { update(it, MediaStore.MediaColumns.IS_TRASHED, 0) } }) return true
        return ask(MediaStore.createTrashRequest(context.contentResolver, uris, false))
    }

    /** Adds to or removes from favorites (a MediaStore flag shared with other apps). Returns false if declined. */
    suspend fun setFavorite(uris: List<Uri>, favorite: Boolean): Boolean {
        val value = if (favorite) 1 else 0
        if (direct { uris.forEach { update(it, MediaStore.MediaColumns.IS_FAVORITE, value) } }) return true
        return ask(MediaStore.createFavoriteRequest(context.contentResolver, uris, favorite))
    }

    /** Deletes permanently. Returns false if the user declines. */
    suspend fun delete(uris: List<Uri>): Boolean {
        if (canManageMedia && direct { uris.forEach { context.contentResolver.delete(it, null, null) } }) return true
        return ask(MediaStore.createDeleteRequest(context.contentResolver, uris))
    }

    /** Runs [block] on the IO dispatcher; returns false if it lacked the permission. */
    private suspend fun direct(block: () -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            block()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** Sets [column] of the file at [uri] to [value]. */
    private fun update(uri: Uri, column: String, value: Int) {
        context.contentResolver.update(uri, ContentValues().apply { put(column, value) }, null, null)
    }
}

/** Remembers a [MediaAccess] wired to this screen, which shows Android's confirmation dialogs. */
@Composable
fun rememberMediaAccess(): MediaAccess {
    val context = LocalContext.current
    val access = remember { MediaAccess(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        access.pending?.invoke(it.resultCode == Activity.RESULT_OK)
        access.pending = null
    }
    access.launch = launcher::launch
    return access
}
