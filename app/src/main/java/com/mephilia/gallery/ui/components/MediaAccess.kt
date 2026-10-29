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

/** L'utilisateur a refusé la demande d'Android. */
class AccessDeniedException : CancellationException("Accès refusé")

/**
 * Modification des médias d'autres applications.
 * Avec l'autorisation "gestion des médias" (Android 12+), on agit directement, sans confirmation ;
 * sinon (ou si Android refuse quand même), on passe par la demande système habituelle.
 */
class MediaAccess internal constructor(private val context: Context) {
    internal var launch: (IntentSenderRequest) -> Unit = {}
    internal var pending: ((Boolean) -> Unit)? = null

    val canManageMedia: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    private suspend fun ask(pi: PendingIntent): Boolean = suspendCancellableCoroutine { cont ->
        pending = { granted -> if (cont.isActive) cont.resume(granted) }
        launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    /** Exécute [block] avec le droit d'écrire sur [uris] ; lève [AccessDeniedException] si refusé. */
    suspend fun write(uris: List<Uri>, block: suspend () -> Unit) {
        if (canManageMedia) {
            try {
                return block()
            } catch (_: SecurityException) {
                // On retombe sur la demande système
            }
        }
        if (!ask(MediaStore.createWriteRequest(context.contentResolver, uris))) throw AccessDeniedException()
        block()
    }

    /** Met à la corbeille ; renvoie false si l'utilisateur refuse. */
    suspend fun trash(uris: List<Uri>): Boolean {
        if (canManageMedia && direct { uris.forEach { update(it, MediaStore.MediaColumns.IS_TRASHED, 1) } }) return true
        return ask(MediaStore.createTrashRequest(context.contentResolver, uris, true))
    }

    /** Supprime définitivement ; renvoie false si l'utilisateur refuse. */
    suspend fun delete(uris: List<Uri>): Boolean {
        if (canManageMedia && direct { uris.forEach { context.contentResolver.delete(it, null, null) } }) return true
        return ask(MediaStore.createDeleteRequest(context.contentResolver, uris))
    }

    private suspend fun direct(block: () -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            block()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun update(uri: Uri, column: String, value: Int) {
        context.contentResolver.update(uri, ContentValues().apply { put(column, value) }, null, null)
    }
}

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
