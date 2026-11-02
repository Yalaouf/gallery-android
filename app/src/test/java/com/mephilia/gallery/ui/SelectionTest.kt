package com.mephilia.gallery.ui

import androidx.compose.runtime.mutableStateOf
import com.mephilia.gallery.media
import com.mephilia.gallery.ui.components.MediaSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectionTest {
    private val items = (1L..10L).map { media(it) }
    private val selection = MediaSelection(mutableStateOf(emptySet()))
    private fun ids(vararg i: Long) = i.toSet()

    @Test
    fun togglesAndClears() {
        selection.toggle(items[0])
        selection.toggle(items[2])
        assertEquals(ids(1, 3), selection.ids)
        selection.toggle(items[0])
        assertEquals(ids(3), selection.ids)
        selection.clear()
        assertFalse(selection.active)
    }

    @Test
    fun dayHeaderSelectsTheWholeDayThenDeselectsIt() {
        selection.toggle(items[0])
        // Partly selected day: tapping the header completes it
        selection.toggleAll(listOf(1, 2, 3))
        assertEquals(ids(1, 2, 3), selection.ids)
        // Fully selected: tapping again clears it, keeping other days
        selection.toggle(items[5])
        selection.toggleAll(listOf(1, 2, 3))
        assertEquals(ids(6), selection.ids)
    }

    @Test
    fun dragSelectsTheRangeInBothDirections() {
        selection.startDrag(items[3], items)
        selection.updateDrag(items[6])
        assertEquals(ids(4, 5, 6, 7), selection.ids)
        // Going back above the start item selects upwards instead
        selection.updateDrag(items[1])
        assertEquals(ids(2, 3, 4), selection.ids)
        selection.endDrag()
    }

    @Test
    fun dragKeepsEarlierSelectionAndShrinksBack() {
        selection.toggle(items[9])
        selection.startDrag(items[0], items)
        selection.updateDrag(items[4])
        selection.updateDrag(items[2])
        // Shrinking the drag unselects what it had added, but not what was selected before
        assertEquals(ids(1, 2, 3, 10), selection.ids)
    }

    @Test
    fun dragStartingOnSelectedItemDeselects() {
        selection.selectAll(items)
        selection.startDrag(items[2], items)
        selection.updateDrag(items[4])
        assertEquals(items.map { it.id }.toSet() - ids(3, 4, 5), selection.ids)
        assertTrue(selection.active)
    }

    @Test
    fun updatesAfterEndDragAreIgnored() {
        selection.startDrag(items[0], items)
        selection.endDrag()
        selection.updateDrag(items[5])
        assertEquals(ids(1), selection.ids)
    }
}
