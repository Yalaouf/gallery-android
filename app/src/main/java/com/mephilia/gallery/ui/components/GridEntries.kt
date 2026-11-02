package com.mephilia.gallery.ui.components

import android.text.format.DateFormat
import com.mephilia.gallery.data.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date pattern skeleton of a day header in the current year: weekday, day and month. */
private const val DAY_SKELETON = "EEEEdMMMM"

/** Date pattern skeleton of a day header in another year: weekday, day, month and year. */
private const val DAY_YEAR_SKELETON = "EEEEdMMMMyyyy"

/** Extra capacity reserved for the day headers, to avoid growing the list while building it. */
private const val EXPECTED_HEADERS = 64

/** Prefix of a day header's key, keeping it distinct from the item ids. */
private const val HEADER_KEY_PREFIX = "h:"

/** An entry of the media grid: a day header spanning the whole row, or an item's cell. */
internal sealed interface GridEntry {
    /**
     * A day header.
     *
     * @param label the day, e.g. "Today" or "Monday 3 November".
     * @param key a stable key for the grid, unique among the headers.
     * @param ids the day's items, so the header can select them all.
     */
    class Header(val label: String, val key: String, val ids: List<Long>) : GridEntry

    /** The cell of an [item]. */
    data class Cell(val item: MediaItem) : GridEntry
}

/**
 * The grid entries for [items], in the same order. With [groupByDate], a header comes before each day's items;
 * it reads [todayLabel] or [yesterdayLabel] for those days, the weekday and date for the others (with the year
 * when it isn't the current one), formatted for [locale].
 */
internal fun buildEntries(
    items: List<MediaItem>,
    groupByDate: Boolean,
    todayLabel: String,
    yesterdayLabel: String,
    locale: Locale,
): List<GridEntry> {
    if (!groupByDate) return items.map { GridEntry.Cell(it) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val dayFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_SKELETON), locale)
    val dayYearFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_YEAR_SKELETON), locale)
    val entries = ArrayList<GridEntry>(items.size + EXPECTED_HEADERS)
    var lastDay: LocalDate? = null
    var dayIds = ArrayList<Long>()
    for (item in items) {
        val day = Instant.ofEpochMilli(item.dateTaken).atZone(zone).toLocalDate()
        if (day != lastDay) {
            lastDay = day
            val label = when {
                day == today -> todayLabel
                day == today.minusDays(1) -> yesterdayLabel
                day.year == today.year -> day.format(dayFormat)
                else -> day.format(dayYearFormat)
            }.replaceFirstChar { it.titlecase(locale) }
            dayIds = ArrayList()
            entries += GridEntry.Header(label, "$HEADER_KEY_PREFIX$day", dayIds)
        }
        dayIds += item.id
        entries += GridEntry.Cell(item)
    }
    return entries
}
