package com.mephilia.gallery.ui.components

import android.content.Context
import android.widget.Toast
import androidx.annotation.PluralsRes
import com.mephilia.gallery.R
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter

/**
 * The folders to offer, filtered by [query] and sorted. When moving items that are all in the same folder,
 * that folder is left out, unless [includeSource] (copying there makes sense).
 */
internal fun destinations(
    albums: List<Album>,
    sourcePaths: Set<String>,
    query: String,
    sortRecent: Boolean,
    includeSource: Boolean,
): List<Album> {
    val normalized = normalizeForSearch(query.trim())
    return albums
        .filter { includeSource || !(sourcePaths.size == 1 && it.relativePath in sourcePaths) }
        .filter {
            normalized.isEmpty() ||
                normalizeForSearch(it.name).contains(normalized) ||
                normalizeForSearch(it.relativePath).contains(normalized)
        }
        .let { list -> if (sortRecent) list.sortedByDescending { it.lastModified } else list.sortedBy { normalizeForSearch(it.name) } }
}

/** Copies [items] to [path] and says how it went. Returns true once done. */
internal suspend fun copyItems(context: Context, writer: MediaWriter, items: List<MediaItem>, path: String): Boolean {
    val failed = writer.copy(items, path)
    showResult(context, R.plurals.copied_to, R.plurals.copy_failed, items.size - failed, failed, path)
    return true
}

/**
 * Moves [items] to [path], after the user allows it if needed, and says how it went.
 * Items already in that folder are left alone. Returns false if the user declined.
 */
internal suspend fun moveItems(
    context: Context,
    writer: MediaWriter,
    access: MediaAccess,
    items: List<MediaItem>,
    path: String,
): Boolean {
    val toMove = items.filter { it.relativePath != path }
    val failed = try {
        access.write(toMove.map { it.uri }) { writer.move(toMove, path) }
        writer.lastMoveFailures
    } catch (_: AccessDeniedException) {
        return false
    }
    showResult(context, R.plurals.moved_to, R.plurals.move_failed, toMove.size - failed, failed, path)
    return true
}

/** Shows how many items [succeeded] (with the folder name), and how many [failed] if any. */
private fun showResult(
    context: Context,
    @PluralsRes successMessage: Int,
    @PluralsRes failureMessage: Int,
    succeeded: Int,
    failed: Int,
    path: String,
) {
    val res = context.resources
    val message = buildString {
        append(res.getQuantityString(successMessage, succeeded, succeeded, folderName(path)))
        if (failed > 0) append(" (").append(res.getQuantityString(failureMessage, failed, failed)).append(")")
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
