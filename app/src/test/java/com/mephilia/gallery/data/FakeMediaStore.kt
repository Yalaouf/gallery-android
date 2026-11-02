package com.mephilia.gallery.data

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileNotFoundException

/**
 * Minimal stand-in for Android's media store: inserts create a file, writes go to it, updates are recorded.
 * Failures can be switched on to check that nothing is lost when Android refuses.
 */
class FakeMediaStore : ContentProvider() {
    class Row(val values: ContentValues, val file: File)

    val rows = LinkedHashMap<Long, Row>()
    var refuseEverything = false
    var failWrites = false
    val refusedPaths = HashSet<String>()
    private var nextId = 1L

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (refuseEverything) return null
        val v = ContentValues(values)
        if (v.getAsString(MediaStore.MediaColumns.RELATIVE_PATH) in refusedPaths) {
            throw IllegalArgumentException("Primary directory not allowed")
        }
        val id = nextId++
        rows[id] = Row(v, File.createTempFile("media", ".bin"))
        return ContentUris.withAppendedId(uri, id)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (failWrites) throw FileNotFoundException("disk full")
        val row = rows[ContentUris.parseId(uri)] ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val row = rows[ContentUris.parseId(uri)] ?: return 0
        row.values.putAll(values)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        if (rows.remove(ContentUris.parseId(uri)) != null) 1 else 0

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
}
