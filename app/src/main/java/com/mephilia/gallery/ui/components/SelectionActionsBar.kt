package com.mephilia.gallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.mephilia.gallery.R
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MetadataWriter
import kotlinx.coroutines.launch

/** The dialogs and sheets the selection actions bar can open; only one is shown at a time. */
private enum class SelectionDialog { Move, Copy, EditDate, RemoveLocation, ConfirmTrash, ConfirmLock }

/** A small top bar for selection mode: close, the number of selected items, and select all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel_selection)) }
        },
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count), fontWeight = FontWeight.Bold) },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all)) }
        },
    )
}

/**
 * The floating bar of actions for the selected [items]: favorite, move, share, delete, and a menu with select all,
 * copy, edit the date, remove the location and hide in the locked folder. [onDone] is called once an action is
 * completed, to leave selection mode.
 *
 * @param albums the folders offered as destinations when moving or copying.
 */
@Composable
fun SelectionActionsBar(
    items: List<MediaItem>,
    albums: List<Album>,
    onDone: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<SelectionDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    val allFavorite = items.isNotEmpty() && items.all { it.favorite }
    val close = { dialog = null }

    FloatingActionBar(modifier) {
        BarAction(
            if (allFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            stringResource(R.string.action_favorite),
        ) { scope.launch { toggleFavorite(access, items) } }
        BarAction(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.action_move)) {
            dialog = SelectionDialog.Move
        }
        BarAction(Icons.Rounded.Share, stringResource(R.string.action_share)) { shareMedia(context, items) }
        BarAction(Icons.Rounded.Delete, stringResource(R.string.action_delete)) {
            if (AppSettings.current.confirmDelete) dialog = SelectionDialog.ConfirmTrash
            else scope.launch { trash(access, items, onDone) }
        }
        Box {
            BarAction(Icons.Rounded.MoreVert, stringResource(R.string.action_more)) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuEntry(Icons.Rounded.SelectAll, R.string.action_select_all) {
                    menu = false
                    onSelectAll()
                }
                MenuEntry(Icons.Rounded.FileCopy, R.string.action_copy_to) {
                    menu = false
                    dialog = SelectionDialog.Copy
                }
                if (items.any(MetadataWriter::canEditDate)) {
                    MenuEntry(Icons.Rounded.CalendarMonth, R.string.action_edit_date) {
                        menu = false
                        dialog = SelectionDialog.EditDate
                    }
                }
                if (items.any(MetadataWriter::canRemoveLocation)) {
                    MenuEntry(Icons.Rounded.LocationOff, R.string.action_remove_location) {
                        menu = false
                        dialog = SelectionDialog.RemoveLocation
                    }
                }
                MenuEntry(Icons.Rounded.Lock, R.string.action_move_to_locked) {
                    menu = false
                    dialog = SelectionDialog.ConfirmLock
                }
            }
        }
    }

    val finished = {
        close()
        onDone()
    }
    when (dialog) {
        SelectionDialog.Move -> MoveFlow(items, albums, onDismiss = close, onMoved = finished)
        SelectionDialog.Copy -> MoveFlow(items, albums, onDismiss = close, onMoved = finished, copy = true)
        SelectionDialog.EditDate -> EditDateDialog(items, onDismiss = close, onDone = finished)
        SelectionDialog.RemoveLocation -> RemoveLocationDialog(items, onDismiss = close, onDone = finished)
        SelectionDialog.ConfirmLock -> ConfirmLockDialog(items.size, onDismiss = close) {
            close()
            scope.launch { if (moveToLocked(context, access, items)) onDone() }
        }
        SelectionDialog.ConfirmTrash -> ConfirmTrashDialog(items.size, onDismiss = close) {
            close()
            scope.launch { trash(access, items, onDone) }
        }
        null -> Unit
    }
}

/** Moves [items] to the trash, after the user allows it if needed, then calls [onDone]. */
private suspend fun trash(access: MediaAccess, items: List<MediaItem>, onDone: () -> Unit) {
    if (access.trash(items.map { it.uri })) {
        MediaEvents.removed(items.map { it.id })
        onDone()
    }
}

/** An entry of the "more" menu: an [icon] and the text of the [label] resource. */
@Composable
private fun MenuEntry(icon: ImageVector, label: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick,
    )
}
