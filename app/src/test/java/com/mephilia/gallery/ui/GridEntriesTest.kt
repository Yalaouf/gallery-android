package com.mephilia.gallery.ui

import com.mephilia.gallery.at
import com.mephilia.gallery.media
import com.mephilia.gallery.ui.components.GridEntry
import com.mephilia.gallery.ui.components.buildEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class GridEntriesTest {
    private val today = LocalDate.now()

    private fun day(daysAgo: Long, hour: Int = 12): Long {
        val d = today.minusDays(daysAgo)
        return at(d.year, d.monthValue, d.dayOfMonth, hour)
    }

    private fun headers(entries: List<GridEntry>) = entries.filterIsInstance<GridEntry.Header>()

    @Test
    fun oneHeaderPerDayWithItsItems() {
        val items = listOf(media(1, day(0, 18)), media(2, day(0, 9)), media(3, day(1)), media(4, day(40)))
        val entries = buildEntries(items, groupByDate = true, "Today", "Yesterday", Locale.ENGLISH)
        val h = headers(entries)
        assertEquals(3, h.size)
        assertEquals("Today", h[0].label)
        assertEquals("Yesterday", h[1].label)
        // Each header knows its day's items, for "select the whole day"
        assertEquals(listOf(1L, 2L), h[0].ids)
        assertEquals(listOf(3L), h[1].ids)
        assertEquals(listOf(4L), h[2].ids)
        // Header, then that day's cells
        assertTrue(entries[0] is GridEntry.Header && entries[1] is GridEntry.Cell && entries[3] is GridEntry.Header)
    }

    @Test
    fun yearIsShownOnlyForPastYears() {
        val lastYear = today.minusYears(1)
        val items = listOf(media(1, at(lastYear.year, 3, 14)))
        val label = headers(buildEntries(items, true, "Today", "Yesterday", Locale.ENGLISH)).single().label
        assertTrue(label, label.contains(lastYear.year.toString()))
        assertTrue(label, label.contains("March"))
    }

    @Test
    fun labelsStartWithACapitalInFrench() {
        val items = listOf(media(1, day(10)))
        val label = headers(buildEntries(items, true, "Aujourd'hui", "Hier", Locale.FRENCH)).single().label
        assertTrue(label, label.first().isUpperCase())
    }

    @Test
    fun withoutGroupingThereAreOnlyCells() {
        val items = listOf(media(1, day(0)), media(2, day(5)))
        val entries = buildEntries(items, groupByDate = false, "Today", "Yesterday", Locale.ENGLISH)
        assertEquals(items, entries.map { (it as GridEntry.Cell).item })
    }
}
