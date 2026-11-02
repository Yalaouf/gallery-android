package com.mephilia.gallery.ui.components

import android.content.Context
import android.widget.Toast
import com.mephilia.gallery.R
import com.mephilia.gallery.data.LockedStore
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem

/**
 * Moves items into the locked folder: copies them to private storage, then deletes the originals.
 * If the deletion is declined, the copies are removed again. Returns true on success.
 */
suspend fun moveToLocked(context: Context, access: MediaAccess, items: List<MediaItem>): Boolean {
    val store = LockedStore(context)
    val copies = ArrayList<MediaItem>()
    try {
        for (item in items) copies += store.add(item)
    } catch (_: Exception) {
        store.discard(copies.map { it.id })
        Toast.makeText(context, context.getString(R.string.lock_failed), Toast.LENGTH_LONG).show()
        return false
    }
    if (!access.delete(items.map { it.uri })) {
        store.discard(copies.map { it.id })
        return false
    }
    MediaEvents.removed(items.map { it.id })
    MediaEvents.changed()
    val count = items.size
    Toast.makeText(context, context.resources.getQuantityString(R.plurals.moved_to_locked, count, count), Toast.LENGTH_SHORT).show()
    return true
}

/** Puts locked items back in the gallery, and says how many made it. */
suspend fun restoreFromLocked(context: Context, items: List<MediaItem>) {
    val failed = LockedStore(context).restore(items)
    MediaEvents.removed(items.map { it.id })
    MediaEvents.changed()
    val restored = items.size - failed
    Toast.makeText(context, context.resources.getQuantityString(R.plurals.restored_from_locked, restored, restored), Toast.LENGTH_SHORT).show()
}

/** Deletes locked items for good. */
suspend fun deleteLocked(context: Context, items: List<MediaItem>) {
    LockedStore(context).discard(items.map { it.id })
    MediaEvents.removed(items.map { it.id })
}

/** Favorites all items, or removes them from favorites if they all are already. */
suspend fun toggleFavorite(access: MediaAccess, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val value = !items.all { it.favorite }
    if (access.setFavorite(items.map { it.uri }, value)) MediaEvents.favorited(items.map { it.id }, value)
}
