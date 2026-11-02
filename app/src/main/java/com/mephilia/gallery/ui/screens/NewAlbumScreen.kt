package com.mephilia.gallery.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaSort
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.AccessDeniedException
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.ThemedSystemBars
import com.mephilia.gallery.ui.components.newFolderPath
import com.mephilia.gallery.ui.components.rememberMediaAccess
import com.mephilia.gallery.ui.components.rememberSelection
import com.mephilia.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Tonal elevation of the bottom bar with the move and copy buttons. */
private val BOTTOM_BAR_ELEVATION = 3.dp

/**
 * Creates an album: Android only lists folders that contain photos or videos, so the album is created
 * by moving or copying the chosen items into Pictures/[name]. This screen is only for picking: every tap
 * selects or deselects an item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAlbumScreen(vm: GalleryViewModel, name: String, onClose: () -> Unit) {
    ThemedSystemBars()
    val context = LocalContext.current
    val media by vm.media.collectAsStateWithLifecycle()
    val sort by vm.mediaSort.collectAsStateWithLifecycle()
    val columns by vm.gridColumns.collectAsStateWithLifecycle()
    val selection = rememberSelection(media)
    val access = rememberMediaAccess()
    val writer = remember { MediaWriter(context) }
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    val path = newFolderPath(name)

    fun finish(copy: Boolean) {
        val chosen = selection.items(media)
        if (chosen.isEmpty()) return
        working = true
        scope.launch {
            try {
                val failed = if (copy) writer.copy(chosen, path) else {
                    access.write(chosen.map { it.uri }) { writer.move(chosen, path) }
                    writer.lastMoveFailures
                }
                val done = chosen.size - failed
                val message = context.resources.getQuantityString(if (copy) R.plurals.copied_to else R.plurals.moved_to, done, done, name)
                MediaEvents.changed()
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                onClose()
            } catch (_: AccessDeniedException) {
                working = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel)) }
                },
                title = {
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (selection.active) pluralStringResource(R.plurals.selected_count, selection.ids.size, selection.ids.size)
                            else stringResource(R.string.new_album_pick),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = BOTTOM_BAR_ELEVATION) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    OutlinedButton(
                        onClick = { finish(copy = true) },
                        enabled = selection.active && !working,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.copy_here)) }
                    Button(
                        onClick = { finish(copy = false) },
                        enabled = selection.active && !working,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.move_here)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            MediaGrid(
                items = media,
                columns = columns,
                groupByDate = sort == MediaSort.DateDesc || sort == MediaSort.DateAsc,
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(bottom = Spacing.l),
                onClick = { selection.toggle(it) },
                selection = selection,
                alwaysSelecting = true,
            )
        }
    }
}
