package com.mephilia.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mephilia.gallery.R
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.data.Prefs
import com.mephilia.gallery.data.Thumb
import com.mephilia.gallery.ui.theme.Dimens
import com.mephilia.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Corner radius of the folder search field: fully rounded. */
private val SEARCH_FIELD_CORNER = 28.dp

/** Opacity of a folder the items can't go to. */
private const val REFUSED_FOLDER_ALPHA = 0.4f

/** The two ways to sort the folders: by name, or most recently changed first. */
private val SORT_CHOICES = listOf(R.string.sort_alphabetical to false, R.string.sort_recent to true)

/**
 * Bottom sheet to move [items] to another folder, or to [copy] them there (the originals stay where they are).
 *
 * The folders can be searched and sorted by name or by most recent change (the choice is remembered). A new folder
 * can be created in Pictures/; when the search matches no folder, the first row offers to create one with that name.
 * Folders that can't take the items (see [refusalReason]) are dimmed with the reason. Once done, a message says how
 * many items were moved or copied, and [onMoved] is called.
 *
 * @param albums the folders offered as destinations. When moving from a single folder, that folder isn't offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveFlow(
    items: List<MediaItem>,
    albums: List<Album>,
    onDismiss: () -> Unit,
    onMoved: () -> Unit,
    copy: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writer = remember { MediaWriter(context) }
    val access = rememberMediaAccess()
    val prefs = remember { Prefs(context) }
    var showNewFolder by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortRecent by remember { mutableStateOf(prefs.moveSortRecent) }
    val sourcePaths = remember(items) { items.map { it.relativePath }.toSet() }
    val targets = remember(albums, sourcePaths, query, sortRecent) {
        destinations(albums, sourcePaths, query, sortRecent, includeSource = copy)
    }

    fun moveTo(path: String) = scope.launch {
        val done = if (copy) copyItems(context, writer, items, path) else moveItems(context, writer, access, items, path)
        if (done) {
            MediaEvents.changed()
            onMoved()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Text(
                pluralStringResource(if (copy) R.plurals.copy_title else R.plurals.move_title, items.size, items.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s),
            )
            FolderSearchField(query, onQueryChange = { query = it })
            FolderSortChoice(sortRecent) {
                sortRecent = it
                prefs.moveSortRecent = it
            }
            LazyColumn {
                item {
                    val name = query.trim()
                    val createNamed = query.isNotBlank() && targets.isEmpty() && isValidFolderName(name)
                    NewFolderRow(createNamed, name) {
                        if (createNamed) moveTo(newFolderPath(name)) else showNewFolder = true
                    }
                }
                items(targets, key = { it.id }) { album ->
                    FolderRow(album, refusalReason(album.relativePath, items)) { moveTo(album.relativePath) }
                }
                item { Spacer(Modifier.size(Spacing.l)) }
            }
        }
    }

    if (showNewFolder) {
        NewFolderDialog(
            initialName = query.trim(),
            confirmLabel = stringResource(if (copy) R.string.copy_here else R.string.move_here),
            onDismiss = { showNewFolder = false },
        ) { name ->
            showNewFolder = false
            moveTo(newFolderPath(name))
        }
    }
}

/** Search field for the folder list, with a button to clear the [query]. */
@Composable
private fun FolderSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_folders)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_clear)) }
        }) else null,
        shape = RoundedCornerShape(SEARCH_FIELD_CORNER),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
    )
}

/** The A → Z / recent switch for the folder list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSortChoice(sortRecent: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        SORT_CHOICES.forEachIndexed { index, (label, recent) ->
            SegmentedButton(
                selected = sortRecent == recent,
                onClick = { onChange(recent) },
                shape = SegmentedButtonDefaults.itemShape(index, SORT_CHOICES.size),
            ) { Text(stringResource(label)) }
        }
    }
}

/**
 * First row of the folder list: "New folder", or "Create folder [name]" when the search matched nothing
 * and [createNamed] is set.
 */
@Composable
private fun NewFolderRow(createNamed: Boolean, name: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(if (createNamed) stringResource(R.string.create_folder_named, name) else stringResource(R.string.new_folder))
        },
        leadingContent = {
            Box(
                Modifier
                    .size(Dimens.listThumbnail)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Dimens.listThumbnailCorner)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CreateNewFolder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * A destination folder: its cover, name and path. When the items can't go there, the row is dimmed,
 * can't be tapped, and shows the [refusal] reason (a string resource) instead of the path.
 */
@Composable
private fun FolderRow(album: Album, refusal: Int?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(refusal?.let { stringResource(it) } ?: album.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            AsyncImage(
                Thumb(album.cover), null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(Dimens.listThumbnail).clip(RoundedCornerShape(Dimens.listThumbnailCorner)),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .alpha(if (refusal == null) 1f else REFUSED_FOLDER_ALPHA)
            .clickable(enabled = refusal == null, onClick = onClick),
    )
}

/** Asks for the name of a new folder (created in Pictures/), starting from [initialName]. */
@Composable
private fun NewFolderDialog(
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val valid = isValidFolderName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
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
                Spacer(Modifier.size(Spacing.s))
                Text(
                    stringResource(R.string.created_in_pictures),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(name) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
