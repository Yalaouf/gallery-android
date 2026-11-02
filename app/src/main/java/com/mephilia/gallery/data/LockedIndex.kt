package com.mephilia.gallery.data

import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Name of the index file; its backup and temporary copy start with the same name. */
private const val INDEX_NAME = "index.json"

/** MIME type of a recovered file whose extension isn't known. */
private const val UNKNOWN_MIME = "application/octet-stream"

/** JSON field of [LockedEntry.id]. */
private const val FIELD_ID = "id"

/** JSON field of [LockedEntry.file]. */
private const val FIELD_FILE = "file"

/** JSON field of [LockedEntry.name]. */
private const val FIELD_NAME = "name"

/** JSON field of [LockedEntry.mime]. */
private const val FIELD_MIME = "mime"

/** JSON field of [LockedEntry.isVideo]. */
private const val FIELD_VIDEO = "video"

/** JSON field of [LockedEntry.dateTaken]. */
private const val FIELD_TAKEN = "taken"

/** JSON field of [LockedEntry.relativePath]. */
private const val FIELD_PATH = "path"

/** JSON field of [LockedEntry.width]. */
private const val FIELD_WIDTH = "w"

/** JSON field of [LockedEntry.height]. */
private const val FIELD_HEIGHT = "h"

/** JSON field of [LockedEntry.durationMs]. */
private const val FIELD_DURATION = "duration"

/**
 * What the locked folder keeps about a hidden item: enough to show it and to put it back where it was.
 *
 * @param id the item's id while locked (negative).
 * @param file name of its file in the locked folder.
 * @param name its original file name.
 * @param relativePath its original folder.
 */
internal data class LockedEntry(
    val id: Long,
    val file: String,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val dateTaken: Long,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
)

/**
 * The index of the locked folder in [dir]: a JSON file listing the hidden items, with a backup one change behind.
 *
 * Losing the index must never make a hidden photo unreachable: if it's damaged, the backup is used, and files
 * neither of them mentions are recovered with what the file itself tells (name, type, date).
 */
internal class LockedIndex(private val dir: File) {
    /** The current index. */
    private val indexFile = File(dir, INDEX_NAME)

    /** The index as it was before the last change, used if the current one is damaged. */
    private val backupFile = File(dir, "$INDEX_NAME.bak")

    /** Where a new index is written before replacing the current one. */
    private val tempFile = File(dir, "$INDEX_NAME.tmp")

    /**
     * The entries of the index (or its backup), plus the files neither mentions. The backup is one change
     * behind, so it may list files deleted since; entries whose file is gone are dropped.
     */
    fun read(): List<LockedEntry> {
        val indexed = (parse(indexFile) ?: parse(backupFile).orEmpty()).filter { File(dir, it.file).exists() }
        val known = indexed.mapTo(HashSet()) { it.file }
        val orphans = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name !in known && !it.name.startsWith(INDEX_NAME) }
            .mapNotNull(::recover)
        return indexed + orphans
    }

    /**
     * Replaces the index with [entries]. The current index first becomes the backup, unless it's damaged
     * (the backup is then the better copy). The new index is written, synced, then moved over the old one in a
     * single step, so a crash can't leave a half-written index.
     *
     * @throws java.io.IOException if the index can't be replaced, rather than silently keeping the old one.
     */
    fun write(entries: List<LockedEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        if (parse(indexFile) != null) indexFile.copyTo(backupFile, overwrite = true)
        FileOutputStream(tempFile).use { out ->
            out.write(array.toString().toByteArray())
            out.fd.sync()
        }
        Files.move(tempFile.toPath(), indexFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** An entry for a locked [file] the index doesn't mention, from its name; null if it's not a locked file. */
    private fun recover(file: File): LockedEntry? {
        val id = file.name.substringBefore('.').toLongOrNull()?.let { -it } ?: return null
        val extension = file.name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: UNKNOWN_MIME
        return LockedEntry(
            id, file.name, file.name, mime, isVideoMime(mime), file.lastModified(),
            relativePath = "", width = 0, height = 0, durationMs = 0,
        )
    }

    /** The entries of an index [file], or null if it's missing or unreadable. */
    private fun parse(file: File): List<LockedEntry>? {
        if (!file.exists()) return null
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { array.getJSONObject(it).toEntry() }
        }.getOrNull()
    }

    /** This entry as a JSON object of the index. */
    private fun LockedEntry.toJson() = JSONObject().apply {
        put(FIELD_ID, id)
        put(FIELD_FILE, file)
        put(FIELD_NAME, name)
        put(FIELD_MIME, mime)
        put(FIELD_VIDEO, isVideo)
        put(FIELD_TAKEN, dateTaken)
        put(FIELD_PATH, relativePath)
        put(FIELD_WIDTH, width)
        put(FIELD_HEIGHT, height)
        put(FIELD_DURATION, durationMs)
    }

    /** The entry this JSON object of the index describes. */
    private fun JSONObject.toEntry() = LockedEntry(
        id = getLong(FIELD_ID),
        file = getString(FIELD_FILE),
        name = getString(FIELD_NAME),
        mime = getString(FIELD_MIME),
        isVideo = getBoolean(FIELD_VIDEO),
        dateTaken = getLong(FIELD_TAKEN),
        relativePath = optString(FIELD_PATH),
        width = optInt(FIELD_WIDTH),
        height = optInt(FIELD_HEIGHT),
        durationMs = optLong(FIELD_DURATION),
    )
}
