package com.mephilia.gallery.data

import android.content.Context

/** Name of the preferences file of the view choices. */
private const val FILE_NAME = "gallery"

/** Preference key of [Prefs.mediaSort]. */
private const val KEY_MEDIA_SORT = "media_sort"

/** Preference key of [Prefs.mediaFilter]. */
private const val KEY_MEDIA_FILTER = "media_filter"

/** Preference key of [Prefs.albumSort]. */
private const val KEY_ALBUM_SORT = "album_sort"

/** Preference key of [Prefs.albumSortDescending]. */
private const val KEY_ALBUM_SORT_DESCENDING = "album_sort_desc"

/** Preference key of [Prefs.moveSortRecent]. */
private const val KEY_MOVE_SORT_RECENT = "move_sort_recent"

/** Preference key of [Prefs.gridColumns]. */
private const val KEY_GRID_COLUMNS = "grid_columns"

/** Number of columns of the photo grid until the user picks another. */
const val DEFAULT_GRID_COLUMNS = 4

/**
 * The view choices remembered between launches: sorts, filter and grid size.
 * The settings chosen in the settings screen are in [AppSettings].
 */
class Prefs(context: Context) {
    /** Where the view choices are stored. */
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** How the photos are sorted. */
    var mediaSort: MediaSort
        get() = enumOr(preferences.getString(KEY_MEDIA_SORT, null), MediaSort.DateDesc)
        set(value) = preferences.edit().putString(KEY_MEDIA_SORT, value.name).apply()

    /** Which items the photos tab shows. */
    var mediaFilter: MediaFilter
        get() = enumOr(preferences.getString(KEY_MEDIA_FILTER, null), MediaFilter.All)
        set(value) = preferences.edit().putString(KEY_MEDIA_FILTER, value.name).apply()

    /** How the albums are sorted. */
    var albumSort: AlbumSort
        get() = enumOr(preferences.getString(KEY_ALBUM_SORT, null), AlbumSort.Modified)
        set(value) = preferences.edit().putString(KEY_ALBUM_SORT, value.name).apply()

    /** Whether the albums are sorted in descending order. */
    var albumSortDescending: Boolean
        get() = preferences.getBoolean(KEY_ALBUM_SORT_DESCENDING, true)
        set(value) = preferences.edit().putBoolean(KEY_ALBUM_SORT_DESCENDING, value).apply()

    /** Whether the folders of the move and copy sheet are sorted by most recent change (otherwise by name). */
    var moveSortRecent: Boolean
        get() = preferences.getBoolean(KEY_MOVE_SORT_RECENT, false)
        set(value) = preferences.edit().putBoolean(KEY_MOVE_SORT_RECENT, value).apply()

    /** Number of columns of the photo grid. */
    var gridColumns: Int
        get() = preferences.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)
        set(value) = preferences.edit().putInt(KEY_GRID_COLUMNS, value).apply()

    /** The value of [E] called [name], or [default] if there is none (unset, or from an older version). */
    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
