package com.mephilia.gallery.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.data.Thumb
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.mephilia.gallery.R
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.rememberModalBottomSheetState
import com.mephilia.gallery.data.Prefs
import java.text.Normalizer
import com.mephilia.gallery.data.MediaEvents

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

@Composable
fun SelectionActionsBar(
    items: List<MediaItem>,
    albums: List<Album>,
    onDone: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var moving by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.shadow(16.dp, CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BarAction(Icons.Rounded.SelectAll, stringResource(R.string.action_all)) { onSelectAll() }
            BarAction(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.action_move)) { moving = true }
            BarAction(Icons.Rounded.Share, stringResource(R.string.action_share)) { shareMedia(context, items) }
            BarAction(Icons.Rounded.Delete, stringResource(R.string.action_delete)) {
                if (AppSettings.current.confirmDelete) confirmTrash = true
                else scope.launch { trash(access, items, onDone) }
            }
        }
    }

    if (confirmTrash) {
        ConfirmTrashDialog(items.size, onDismiss = { confirmTrash = false }) {
            confirmTrash = false
            scope.launch { trash(access, items, onDone) }
        }
    }

    if (moving) {
        MoveFlow(items, albums, onDismiss = { moving = false }, onMoved = { moving = false; onDone() })
    }
}

private suspend fun trash(access: MediaAccess, items: List<MediaItem>, onDone: () -> Unit) {
    if (access.trash(items.map { it.uri })) {
        MediaEvents.removed(items.map { it.id })
        onDone()
    }
}

@Composable
fun ConfirmTrashDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text(pluralStringResource(R.plurals.trash_title, count, count)) },
        text = { Text(stringResource(R.string.trash_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
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

fun shareMedia(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items[0].mimeType
            putExtra(Intent.EXTRA_STREAM, items[0].uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = when {
                items.all { !it.isVideo } -> "image/*"
                items.all { it.isVideo } -> "video/*"
                else -> "*/*"
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(items.map { it.uri }))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

/** Lowercase without accents, so "tele" matches "Télé". */
internal fun normalizeForSearch(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

private fun isValidFolderName(name: String) =
    name.isNotBlank() && name.none { it in "/\\:*?\"<>|" } && !name.startsWith(".")

private val audioDirs = setOf("Music", "Podcasts", "Ringtones", "Alarms", "Notifications", "Audiobooks", "Recordings")

/**
 * MediaStore only accepts images in some top-level folders (not Movies/ nor audio folders), and videos
 * not in audio folders. Returns the reason a folder is refused, or null if it's allowed.
 */
@StringRes
private fun refusalReason(relativePath: String, items: List<MediaItem>): Int? {
    val top = relativePath.substringBefore('/')
    return when {
        top in audioDirs -> R.string.folder_audio_only
        top == "Movies" && items.any { !it.isVideo } -> R.string.folder_no_photos
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveFlow(
    items: List<MediaItem>,
    albums: List<Album>,
    onDismiss: () -> Unit,
    onMoved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writer = remember { MediaWriter(context) }
    var newFolder by remember { mutableStateOf(false) }
    val prefs = remember { Prefs(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortRecent by remember { mutableStateOf(prefs.moveSortRecent) }

    val sourcePaths = remember(items) { items.map { it.relativePath }.toSet() }
    val targets = remember(albums, sourcePaths, query, sortRecent) {
        val q = normalizeForSearch(query.trim())
        albums
            .filter { !(sourcePaths.size == 1 && it.relativePath in sourcePaths) }
            .filter { q.isEmpty() || normalizeForSearch(it.name).contains(q) || normalizeForSearch(it.relativePath).contains(q) }
            .let { list ->
                if (sortRecent) list.sortedByDescending { it.lastModified } else list.sortedBy { normalizeForSearch(it.name) }
            }
    }

    val access = rememberMediaAccess()

    fun moveTo(path: String) {
        scope.launch {
            val toMove = items.filter { it.relativePath != path }
            val failed = try {
                access.write(toMove.map { it.uri }) { writer.move(toMove, path) }
                writer.lastMoveFailures
            } catch (_: AccessDeniedException) {
                return@launch
            }
            val moved = toMove.size - failed
            val folder = path.trimEnd('/').substringAfterLast('/')
            val res = context.resources
            val message = buildString {
                append(res.getQuantityString(R.plurals.moved_to, moved, moved, folder))
                if (failed > 0) append(" (").append(res.getQuantityString(R.plurals.move_failed, failed, failed)).append(")")
            }
            MediaEvents.changed()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onMoved()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fully expanded so the list stays visible above the keyboard while searching
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Text(
                pluralStringResource(R.plurals.move_title, items.size, items.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_folders)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_clear)) }
                }) else null,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(R.string.sort_alphabetical to false, R.string.sort_recent to true).forEachIndexed { i, (label, recent) ->
                    SegmentedButton(
                        selected = sortRecent == recent,
                        onClick = {
                            sortRecent = recent
                            prefs.moveSortRecent = recent
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(stringResource(label)) }
                }
            }
            LazyColumn {
                item {
                    val createNamed = query.isNotBlank() && targets.isEmpty() && isValidFolderName(query.trim())
                    ListItem(
                        headlineContent = {
                            Text(
                                if (createNamed) stringResource(R.string.create_folder_named, query.trim())
                                else stringResource(R.string.new_folder),
                            )
                        },
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.CreateNewFolder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable {
                            if (createNamed) moveTo("Pictures/${query.trim()}/") else newFolder = true
                        },
                    )
                }
                items(targets, key = { it.id }) { album ->
                    val reason = refusalReason(album.relativePath, items)
                    ListItem(
                        headlineContent = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(reason?.let { stringResource(it) } ?: album.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            AsyncImage(
                                Thumb(album.cover), null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .alpha(if (reason == null) 1f else 0.4f)
                            .clickable(enabled = reason == null) { moveTo(album.relativePath) },
                    )
                }
                item { Spacer(Modifier.size(16.dp)) }
            }
        }
    }

    if (newFolder) {
        var name by remember { mutableStateOf(query.trim()) }
        val valid = isValidFolderName(name)
        AlertDialog(
            onDismissRequest = { newFolder = false },
            icon = { Icon(Icons.Rounded.CreateNewFolder, null) },
            title = { Text(stringResource(R.string.new_folder)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.folder_name)) },
                        isError = name.isNotEmpty() && !valid,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.created_in_pictures),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    newFolder = false
                    // Pictures/ accepts both images and videos
                    moveTo("Pictures/${name.trim()}/")
                }) { Text(stringResource(R.string.move_here)) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}


class MediaSelection internal constructor(private val state: MutableState<Set<Long>>) {
    val ids: Set<Long> get() = state.value
    val active: Boolean get() = state.value.isNotEmpty()
    fun toggle(item: MediaItem) {
        state.value = if (item.id in ids) ids - item.id else ids + item.id
    }
    fun selectAll(items: List<MediaItem>) {
        state.value = items.mapTo(HashSet()) { it.id }
    }
    fun clear() {
        state.value = emptySet()
    }

    /** Selects all [groupIds], or deselects them if they were all selected. */
    fun toggleAll(groupIds: List<Long>) {
        state.value = if (ids.containsAll(groupIds)) ids - groupIds.toSet() else ids + groupIds
    }
    fun items(all: List<MediaItem>) = all.filter { it.id in ids }

    private var dragOrder: List<MediaItem> = emptyList()
    private var dragBase: Set<Long> = emptySet()
    private var dragAnchor = -1
    private var dragAdds = true

    /** [order] is the display order, used to select everything between the start item and the finger. */
    fun startDrag(item: MediaItem, order: List<MediaItem>) {
        dragOrder = order
        dragBase = ids
        dragAnchor = order.indexOfFirst { it.id == item.id }
        // Starting on a selected item makes the drag deselect
        dragAdds = item.id !in ids
        updateDrag(item)
    }

    fun updateDrag(item: MediaItem) {
        if (dragAnchor < 0) return
        val index = dragOrder.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val range = dragOrder.subList(minOf(dragAnchor, index), maxOf(dragAnchor, index) + 1).map { it.id }
        state.value = if (dragAdds) dragBase + range else dragBase - range.toSet()
    }

    fun endDrag() {
        dragAnchor = -1
        dragOrder = emptyList()
    }
}

@Composable
fun rememberSelection(items: List<MediaItem>): MediaSelection {
    val state = rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    val selection = remember(state) { MediaSelection(state) }
    // Forget items that disappeared (moved out of the view, deleted…)
    LaunchedEffect(items) {
        val present = items.mapTo(HashSet()) { it.id }
        if (!present.containsAll(selection.ids)) state.value = selection.ids.filterTo(HashSet()) { it in present }
    }
    BackHandler(enabled = selection.active) { selection.clear() }
    return selection
}
