package com.mephilia.gallery.ui.screens.viewer

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.AccessDeniedException
import com.mephilia.gallery.ui.components.ConfirmDeleteLockedDialog
import com.mephilia.gallery.ui.components.ConfirmLockDialog
import com.mephilia.gallery.ui.components.ConfirmTrashDialog
import com.mephilia.gallery.ui.components.MediaAccess
import com.mephilia.gallery.ui.components.MoveFlow
import com.mephilia.gallery.ui.components.deleteLocked
import com.mephilia.gallery.ui.components.isValidFolderName
import com.mephilia.gallery.ui.components.moveToLocked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The viewer acts on one item at a time, which some shared dialogs need to word their text. */
private const val SINGLE_ITEM = 1

/**
 * Shows [dialog] for [item], the item currently in the viewer, and carries out what the user confirms in it.
 * [onDismiss] closes the dialog, both when it's cancelled and once its action is started.
 *
 * @param scope runs the confirmed actions. It must outlive the dialog (the viewer's scope), since the dialog
 * closes as soon as the action starts.
 */
@Composable
internal fun ViewerDialogHost(
    dialog: ViewerDialog,
    item: MediaItem,
    access: MediaAccess,
    albums: List<Album>,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val items = listOf(item)

    fun confirmed(action: suspend () -> Unit) {
        onDismiss()
        scope.launch { action() }
    }

    when (dialog) {
        ViewerDialog.Info -> MediaInfoSheet(item, onDismiss)
        ViewerDialog.Move -> MoveFlow(items, albums, onDismiss = onDismiss, onMoved = onDismiss)
        ViewerDialog.Copy -> MoveFlow(items, albums, onDismiss = onDismiss, onMoved = onDismiss, copy = true)
        ViewerDialog.Rename -> RenameDialog(item, access, onDismiss)
        ViewerDialog.ConfirmTrash -> ConfirmTrashDialog(SINGLE_ITEM, onDismiss = onDismiss) {
            confirmed { trashItem(access, item) }
        }
        ViewerDialog.ConfirmLock -> ConfirmLockDialog(SINGLE_ITEM, onDismiss = onDismiss) {
            confirmed { moveToLocked(context, access, items) }
        }
        ViewerDialog.ConfirmDeleteLocked -> ConfirmDeleteLockedDialog(SINGLE_ITEM, onDismiss) {
            confirmed { deleteLocked(context, items) }
        }
    }
}

/**
 * Renames [item]'s file. Only the name is edited; the extension is shown as a fixed suffix and kept.
 * The dialog stays open if renaming fails, with the reason in a message, or if the user declines the
 * permission request; it closes once the file is renamed.
 */
@Composable
private fun RenameDialog(item: MediaItem, access: MediaAccess, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extension = item.name.substringAfterLast('.', "")
    var name by remember { mutableStateOf(item.name.substringBeforeLast('.', item.name)) }
    val valid = isValidFolderName(name)

    fun rename() = scope.launch {
        try {
            val writer = MediaWriter(context)
            access.write(listOf(item.uri)) { writer.rename(item, name.trim()) }
            MediaEvents.changed()
            onDismiss()
        } catch (_: AccessDeniedException) {
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: context.getString(R.string.error_rename), Toast.LENGTH_LONG).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                suffix = if (extension.isNotEmpty()) ({ Text(".$extension") }) else null,
                isError = name.isNotEmpty() && !valid,
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { rename() }) { Text(stringResource(R.string.action_rename)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
