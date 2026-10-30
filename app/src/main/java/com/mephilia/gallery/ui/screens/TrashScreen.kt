package com.mephilia.gallery.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.MediaAccess
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.SelectionTopBar
import com.mephilia.gallery.ui.components.rememberMediaAccess
import com.mephilia.gallery.ui.components.rememberSelection
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** Android's trash: items deleted in the last 30 days, which can be restored or deleted for good. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    val items by vm.trash.collectAsStateWithLifecycle()
    val columns by vm.gridColumns.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.reloadTrash() }
    val selection = rememberSelection(items)
    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    SystemBars(lightIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection.ids.size, onClose = selection::clear, onSelectAll = { selection.selectAll(items) })
            } else TopAppBar(
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    MediaGrid(
                        items = items,
                        columns = columns,
                        groupByDate = false,
                        state = gridState,
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp,
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
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp),
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

/** Restore / delete for good, for a selection in the trash or the item shown in the viewer. */
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
    Surface(
        modifier = modifier.shadow(16.dp, CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onSelectAll != null) TrashAction(Icons.Rounded.SelectAll, stringResource(R.string.action_all), onSelectAll)
            TrashAction(Icons.Rounded.RestoreFromTrash, stringResource(R.string.action_restore)) {
                scope.launch { if (restore(access, items)) onDone() }
            }
            TrashAction(Icons.Rounded.DeleteForever, stringResource(R.string.action_delete_forever)) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteForeverDialog(items.size, onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            scope.launch { if (deleteForever(access, items)) onDone() }
        }
    }
}

@Composable
private fun TrashAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ConfirmDeleteForeverDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteForever, null) },
        title = { Text(pluralStringResource(R.plurals.delete_forever_title, count, count)) },
        text = { Text(stringResource(R.string.delete_forever_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete_forever)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private suspend fun restore(access: MediaAccess, items: List<MediaItem>): Boolean {
    if (items.isEmpty() || !access.restore(items.map { it.uri })) return false
    MediaEvents.removed(items.map { it.id })
    MediaEvents.changed()
    return true
}

private suspend fun deleteForever(access: MediaAccess, items: List<MediaItem>): Boolean {
    if (items.isEmpty() || !access.delete(items.map { it.uri })) return false
    MediaEvents.removed(items.map { it.id })
    return true
}

private fun daysLeftLabel(context: Context, item: MediaItem): String? {
    if (item.expiresAt <= 0) return null
    val days = ceil((item.expiresAt - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0)
    return context.resources.getQuantityString(R.plurals.days_left, days, days)
}
