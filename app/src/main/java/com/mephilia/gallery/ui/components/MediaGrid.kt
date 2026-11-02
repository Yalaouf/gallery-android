package com.mephilia.gallery.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem

/** Gap between the thumbnails of the grid. */
private val GRID_GAP = 2.dp

/**
 * The grid of photos and videos used by the photos tab, albums, trash and pickers, with a fast scroller.
 *
 * With a [selection], items can be selected by tapping in selection mode or by long-pressing and dragging,
 * and with [groupByDate], tapping a day header selects the whole day.
 *
 * @param columns the number of columns.
 * @param groupByDate groups the items under a header per day; the fast scroller then shows dates too.
 * @param contentPadding padding of the grid's content; its bottom also applies to the fast scroller.
 * @param onClick called when an item is tapped (outside a drag selection).
 * @param badge an optional label shown on each thumbnail (e.g. days left in the trash).
 * @param header extra full-width entries above the items (banners, "on this day"…).
 * @param alwaysSelecting shows the selection circles even when nothing is selected yet (picker screens).
 */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    groupByDate: Boolean,
    state: LazyGridState,
    contentPadding: PaddingValues,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    selection: MediaSelection? = null,
    badge: ((MediaItem) -> String?)? = null,
    header: (LazyGridScope.() -> Unit)? = null,
    alwaysSelecting: Boolean = false,
) {
    val today = stringResource(R.string.today)
    val yesterday = stringResource(R.string.yesterday)
    val locale = LocalConfiguration.current.locales[0]
    val entries = remember(items, groupByDate, locale) { buildEntries(items, groupByDate, today, yesterday, locale) }
    val byId = remember(items) { items.associateBy { it.id } }
    val selected = selection?.ids.orEmpty()
    val selectionMode = alwaysSelecting || selected.isNotEmpty()

    fun itemAt(position: Offset): MediaItem? {
        val info = state.layoutInfo
        val y = position.y + info.viewportStartOffset
        val hit = info.visibleItemsInfo.firstOrNull {
            position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
                y >= it.offset.y && y < it.offset.y + it.size.height
        } ?: return null
        return byId[hit.key as? Long ?: return null]
    }

    val dragSelect = rememberDragSelectModifier(state, items, selection, ::itemAt)

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            modifier = Modifier.fillMaxSize().then(dragSelect),
        ) {
            header?.invoke(this)
            items(
                entries,
                key = { entry ->
                    when (entry) {
                        is GridEntry.Header -> entry.key
                        is GridEntry.Cell -> entry.item.id
                    }
                },
                span = { entry -> if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { entry -> entry::class },
            ) { entry ->
                when (entry) {
                    is GridEntry.Header -> DayHeader(
                        label = entry.label,
                        selectionMode = selectionMode,
                        allSelected = entry.ids.all { it in selected },
                        onToggle = { selection?.toggleAll(entry.ids) },
                        modifier = snappyItemModifier(),
                    )
                    is GridEntry.Cell -> MediaThumbnail(
                        item = entry.item,
                        modifier = snappyItemModifier(),
                        selectionMode = selectionMode,
                        isSelected = entry.item.id in selected,
                        badge = badge?.invoke(entry.item),
                    ) { onClick(entry.item) }
                }
            }
        }
        FastScroller(
            state = state,
            dateAt = { index -> dateAtIndex(index, state, entries, byId) },
            showDates = groupByDate,
            topPadding = 0.dp,
            bottomPadding = contentPadding.calculateBottomPadding(),
        )
    }
}

/**
 * The date of the grid entry at [index], for the fast scroller's bubble. Headers use their day.
 * The extra entries above the items (see MediaGrid's header) are counted out.
 */
private fun dateAtIndex(index: Int, state: LazyGridState, entries: List<GridEntry>, byId: Map<Long, MediaItem>): Long? {
    val headerCount = state.layoutInfo.totalItemsCount - entries.size
    val entry = entries.getOrNull((index - headerCount).coerceIn(0, entries.lastIndex))
    return when (entry) {
        is GridEntry.Cell -> entry.item.dateTaken
        is GridEntry.Header -> byId[entry.ids.firstOrNull()]?.dateTaken
        null -> null
    }
}

/**
 * Animates grid items into place: removed items vanish at once (a fade-out reads as a delay after deleting),
 * and their neighbours slide into place quickly.
 */
fun LazyGridItemScope.snappyItemModifier(): Modifier =
    Modifier.animateItem(
        fadeOutSpec = null,
        placementSpec = spring(stiffness = Spring.StiffnessMedium, visibilityThreshold = IntOffset.VisibilityThreshold),
    )
