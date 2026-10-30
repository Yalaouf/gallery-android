package com.mephilia.gallery.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("gallery", Context.MODE_PRIVATE)

    var mediaSort: MediaSort
        get() = enumOr(sp.getString("media_sort", null), MediaSort.DateDesc)
        set(v) = sp.edit().putString("media_sort", v.name).apply()

    var mediaFilter: MediaFilter
        get() = enumOr(sp.getString("media_filter", null), MediaFilter.All)
        set(v) = sp.edit().putString("media_filter", v.name).apply()

    var albumSort: AlbumSort
        get() = enumOr(sp.getString("album_sort", null), AlbumSort.Modified)
        set(v) = sp.edit().putString("album_sort", v.name).apply()

    var albumSortDescending: Boolean
        get() = sp.getBoolean("album_sort_desc", true)
        set(v) = sp.edit().putBoolean("album_sort_desc", v).apply()

    var moveSortRecent: Boolean
        get() = sp.getBoolean("move_sort_recent", false)
        set(v) = sp.edit().putBoolean("move_sort_recent", v).apply()

    var gridColumns: Int
        get() = sp.getInt("grid_columns", 4)
        set(v) = sp.edit().putInt("grid_columns", v).apply()

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
