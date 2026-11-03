package com.mephilia.gallery.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import kotlinx.coroutines.launch

/** An action on a whole album, from its screen's menu or a long-press on its card. */
enum class AlbumAction { Rename, Delete }

/**
 * The album actions as menu entries. Renaming is only offered when [canRename]: top-level folders such as
 * "Pictures/" are Android's own (see [renamedFolderPath]).
 */
@Composable
fun AlbumMenuEntries(canRename: Boolean, onAction: (AlbumAction) -> Unit) {
    if (canRename) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename_album)) },
            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
            onClick = { onAction(AlbumAction.Rename) },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_delete_album)) },
        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
        onClick = { onAction(AlbumAction.Delete) },
    )
}

/** Whether the folder at [relativePath] can be renamed (it isn't a top-level folder). */
fun canRenameFolder(relativePath: String) = renamedFolderPath(relativePath, newName = PROBE_NAME) != null

/** Any valid name, to check whether a folder can be renamed at all. */
private const val PROBE_NAME = "x"

/**
 * Shows the dialog of [action] for the folder at [relativePath], called [name], and carries out the action on
 * all its [items] (whatever the photos / videos filter shows).
 *
 * The dialog stays open while the action runs, since Android may ask the user for permission. A message tells
 * how it went.
 *
 * @param albums every folder, to refuse a new name that's already taken.
 * @param onDone the folder was renamed or deleted: it no longer exists under its old path.
 */
@Composable
fun AlbumActionDialog(
    action: AlbumAction,
    name: String,
    relativePath: String,
    items: List<MediaItem>,
    albums: List<Album>,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    when (action) {
        AlbumAction.Rename -> RenameAlbumDialog(name, relativePath, items, albums, onDismiss, onDone)
        AlbumAction.Delete -> DeleteAlbumDialog(name, items, onDismiss, onDone)
    }
}

/**
 * Asks for the album's new name, starting from the current one. The name follows the same rules as new
 * folders, and can't be one already used by another folder.
 */
@Composable
private fun RenameAlbumDialog(
    name: String,
    relativePath: String,
    items: List<MediaItem>,
    albums: List<Album>,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf(name) }
    var working by remember { mutableStateOf(false) }
    val newPath = renamedFolderPath(relativePath, newName)
    val unchanged = newPath.equals(relativePath, ignoreCase = true)
    val taken = newPath != null && !unchanged && isFolderTaken(newPath, albums)
    val valid = isValidFolderName(newName.trim()) && newPath != null && !unchanged && !taken

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
        title = { Text(stringResource(R.string.action_rename_album)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name)) },
                isError = taken || (newName.isNotEmpty() && !isValidFolderName(newName.trim())),
                supportingText = if (taken) ({ Text(stringResource(R.string.album_name_taken)) }) else null,
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid && !working,
                onClick = {
                    val target = newPath ?: return@TextButton
                    working = true
                    scope.launch {
                        if (renameAlbum(context, access, items, target)) onDone() else working = false
                    }
                },
            ) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Asks before moving every item of the album to the trash, saying how many there are. */
@Composable
private fun DeleteAlbumDialog(name: String, items: List<MediaItem>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    ConfirmDialog(
        icon = Icons.Rounded.Delete,
        title = stringResource(R.string.delete_album_title, name),
        text = pluralStringResource(R.plurals.delete_album_text, items.size, items.size),
        confirmLabel = stringResource(R.string.action_delete),
        onDismiss = { if (!working) onDismiss() },
        onConfirm = {
            if (working) return@ConfirmDialog
            working = true
            scope.launch {
                if (deleteAlbum(context, access, items)) onDone() else onDismiss()
            }
        },
    )
}

/**
 * Moves every item of the album to [newPath], after the user allows it if needed, and says how it went.
 * Returns false if the user declined. Items the new folder refuses are counted in the message.
 */
private suspend fun renameAlbum(context: Context, access: MediaAccess, items: List<MediaItem>, newPath: String): Boolean {
    val writer = MediaWriter(context)
    try {
        access.write(items.map { it.uri }) { writer.move(items, newPath) }
    } catch (_: AccessDeniedException) {
        return false
    }
    MediaEvents.changed()
    val failed = writer.lastMoveFailures
    val message = buildString {
        append(context.getString(R.string.album_renamed, folderName(newPath)))
        if (failed > 0) append(" (").append(context.resources.getQuantityString(R.plurals.move_failed, failed, failed)).append(")")
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    return true
}

/** Moves every item of the album to the trash, after the user allows it. Returns false if the user declined. */
private suspend fun deleteAlbum(context: Context, access: MediaAccess, items: List<MediaItem>): Boolean {
    if (!access.trash(items.map { it.uri })) return false
    MediaEvents.removed(items.map { it.id })
    Toast.makeText(context, context.getString(R.string.album_deleted), Toast.LENGTH_SHORT).show()
    return true
}
