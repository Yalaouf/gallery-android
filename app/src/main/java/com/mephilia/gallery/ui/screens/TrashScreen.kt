package com.mephilia.gallery.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.BarAction
import com.mephilia.gallery.ui.components.ConfirmDialog
import com.mephilia.gallery.ui.components.EmptyState
import com.mephilia.gallery.ui.components.FloatingActionBar
import com.mephilia.gallery.ui.components.MediaAccess
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.SelectionTopBar
import com.mephilia.gallery.ui.components.ThemedSystemBars
import com.mephilia.gallery.ui.components.rememberMediaAccess
import com.mephilia.gallery.ui.components.rememberSelection
import com.mephilia.gallery.ui.theme.Dimens
import com.mephilia.gallery.ui.theme.Spacing
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlinx.coroutines.launch

/** Milliseconds in a day, to count the days left before an item is deleted for good. */
private val MILLIS_PER_DAY = TimeUnit.DAYS.toMillis(1).toDouble()

/**
 * Android's trash: items deleted in the last 30 days, which can be restored or deleted for good. Each thumbnail
 * says how many days are left before Android deletes it, and the whole trash can be emptied at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    val items by vm.trash.collectAsStateWithLifecycle()
    val columns by vm.gridColumns.collectAsStateWithLifecycle()
    val selection = rememberSelection(items)
    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) { vm.reloadTrash() }
    ThemedSystemBars()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection.ids.size, onClose = selection::clear, onSelectAll = { selection.selectAll(items) })
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                    },
                    title = {
                        Column {
                            Text(stringResource(R.string.trash_title), fontWeight = FontWeight.Bold)
                            Text(
                                pluralStringResource(R.plurals.items_count, items.size, items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { confirmEmpty = true }, enabled = items.isNotEmpty()) {
                            Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.action_empty_trash))
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (items.isEmpty()) {
                EmptyState(Icons.Rounded.Delete, stringResource(R.string.trash_empty))
            } else {
                Column {
                    Text(
                        stringResource(R.string.trash_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                    )
                    MediaGrid(
                        items = items,
                        columns = columns,
                        groupByDate = false,
                        state = gridState,
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Dimens.actionsBarClearance,
                        ),
                        onClick = { if (selection.active) selection.toggle(it) else onOpen(it) },
                        selection = selection,
                        badge = { daysLeftLabel(context, it) },
                    )
                }
            }
            AnimatedVisibility(
                visible = selection.active,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                TrashActionsBar(
                    items = selection.items(items),
                    access = access,
                    onSelectAll = { selection.selectAll(items) },
                    onDone = selection::clear,
                    modifier = Modifier.navigationBarsPadding().padding(bottom = Dimens.floatingBarBottomMargin),
                )
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDeleteForeverDialog(items.size, onDismiss = { confirmEmpty = false }) {
            confirmEmpty = false
            scope.launch { deleteForever(access, items) }
        }
    }
}

/**
 * Restore and delete for good, for a selection in the trash or the item shown in the viewer.
 *
 * @param onSelectAll selects every item; null hides the button (in the viewer).
 * @param onDone called once the items are restored or deleted.
 */
@Composable
fun TrashActionsBar(
    items: List<MediaItem>,
    access: MediaAccess,
    onSelectAll: (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    FloatingActionBar(modifier, horizontalPadding = Spacing.m) {
        if (onSelectAll != null) BarAction(Icons.Rounded.SelectAll, stringResource(R.string.action_all), onSelectAll)
        BarAction(Icons.Rounded.RestoreFromTrash, stringResource(R.string.action_restore)) {
            scope.launch { if (restore(access, items)) onDone() }
        }
        BarAction(Icons.Rounded.DeleteForever, stringResource(R.string.action_delete_forever)) { confirmDelete = true }
    }
    if (confirmDelete) {
        ConfirmDeleteForeverDialog(items.size, onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            scope.launch { if (deleteForever(access, items)) onDone() }
        }
    }
}

/** Asks before deleting [count] items for good. */
@Composable
private fun ConfirmDeleteForeverDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        icon = Icons.Rounded.DeleteForever,
        title = pluralStringResource(R.plurals.delete_forever_title, count, count),
        text = stringResource(R.string.delete_forever_text),
        confirmLabel = stringResource(R.string.action_delete_forever),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** Takes [items] out of the trash, after the user allows it. Returns false if there was nothing done. */
private suspend fun restore(access: MediaAccess, items: List<MediaItem>): Boolean {
    if (items.isEmpty() || !access.restore(items.map { it.uri })) return false
    MediaEvents.removed(items.map { it.id })
    MediaEvents.changed()
    return true
}

/** Deletes [items] for good, after the user allows it. Returns false if there was nothing done. */
private suspend fun deleteForever(access: MediaAccess, items: List<MediaItem>): Boolean {
    if (items.isEmpty() || !access.delete(items.map { it.uri })) return false
    MediaEvents.removed(items.map { it.id })
    return true
}

/** "N days left" before Android deletes [item] for good, or null if that's not known. */
private fun daysLeftLabel(context: Context, item: MediaItem): String? {
    if (item.expiresAt <= 0) return null
    val days = ceil((item.expiresAt - System.currentTimeMillis()) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
    return context.resources.getQuantityString(R.plurals.days_left, days, days)
}
