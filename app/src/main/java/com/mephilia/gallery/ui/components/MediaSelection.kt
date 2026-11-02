package com.mephilia.gallery.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.mephilia.gallery.data.MediaItem

/** Index of the drag anchor when no drag selection is in progress. */
private const val NO_ANCHOR = -1

/**
 * The items selected in a grid, by id. Selection mode is [active] as long as at least one item is selected.
 *
 * Besides tapping items one by one, a long-press followed by a drag selects every item between the one pressed
 * and the one under the finger ([startDrag], [updateDrag], [endDrag]).
 */
class MediaSelection internal constructor(private val state: MutableState<Set<Long>>) {
    /** Ids of the selected items. */
    val ids: Set<Long> get() = state.value

    /** Whether selection mode is on, i.e. something is selected. */
    val active: Boolean get() = state.value.isNotEmpty()

    /** The items in display order during a drag selection. */
    private var dragOrder: List<MediaItem> = emptyList()

    /** What was selected before the drag started, which the drag adds to or removes from. */
    private var dragBase: Set<Long> = emptySet()

    /** Index in [dragOrder] of the item the drag started on, or [NO_ANCHOR] outside a drag. */
    private var dragAnchor = NO_ANCHOR

    /** Whether the current drag selects items (true) or deselects them (false). */
    private var dragAdds = true

    /** Selects [item], or deselects it if it was selected. */
    fun toggle(item: MediaItem) {
        state.value = if (item.id in ids) ids - item.id else ids + item.id
    }

    /** Selects all of [items], and only them. */
    fun selectAll(items: List<MediaItem>) {
        state.value = items.mapTo(HashSet()) { it.id }
    }

    /** Deselects everything, which leaves selection mode. */
    fun clear() {
        state.value = emptySet()
    }

    /** Selects all [groupIds] (e.g. a whole day), or deselects them if they were all selected. */
    fun toggleAll(groupIds: List<Long>) {
        state.value = if (ids.containsAll(groupIds)) ids - groupIds.toSet() else ids + groupIds
    }

    /** The selected items among [all], in the same order. */
    fun items(all: List<MediaItem>) = all.filter { it.id in ids }

    /**
     * Starts a drag selection on [item]. Starting on an item that is already selected makes the drag deselect.
     *
     * @param order the items in display order, used to select everything between the start item and the finger.
     */
    fun startDrag(item: MediaItem, order: List<MediaItem>) {
        dragOrder = order
        dragBase = ids
        dragAnchor = order.indexOfFirst { it.id == item.id }
        dragAdds = item.id !in ids
        updateDrag(item)
    }

    /** Updates the drag selection now that the finger is over [item]. */
    fun updateDrag(item: MediaItem) {
        if (dragAnchor == NO_ANCHOR) return
        val index = dragOrder.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val range = dragOrder.subList(minOf(dragAnchor, index), maxOf(dragAnchor, index) + 1).map { it.id }
        state.value = if (dragAdds) dragBase + range else dragBase - range.toSet()
    }

    /** Ends the drag selection; the selection stays as it is. */
    fun endDrag() {
        dragAnchor = NO_ANCHOR
        dragOrder = emptyList()
    }
}

/**
 * Remembers a [MediaSelection] over [items], kept across configuration changes.
 * Items that disappear (moved out of the view, deleted…) are forgotten, and the back button leaves selection mode.
 */
@Composable
fun rememberSelection(items: List<MediaItem>): MediaSelection {
    val state = rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    val selection = remember(state) { MediaSelection(state) }
    LaunchedEffect(items) {
        val present = items.mapTo(HashSet()) { it.id }
        if (!present.containsAll(selection.ids)) state.value = selection.ids.filterTo(HashSet()) { it in present }
    }
    BackHandler(enabled = selection.active) { selection.clear() }
    return selection
}
