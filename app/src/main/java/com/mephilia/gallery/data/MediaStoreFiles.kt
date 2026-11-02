package com.mephilia.gallery.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.FileOutputStream
import java.io.InputStream

/** File mode that creates or overwrites a file. */
const val MODE_WRITE = "w"

/** File mode that overwrites a file and cuts what's left of its previous content. */
const val MODE_WRITE_TRUNCATE = "wt"

/** Value of IS_PENDING while a new file is being written: other apps don't see it yet. */
private const val PENDING = 1

/** Value of IS_PENDING once the file is complete. */
private const val PUBLISHED = 0

/** MIME type prefix of videos. */
private const val VIDEO_MIME_PREFIX = "video/"

/** Whether [mime] is a video type. */
fun isVideoMime(mime: String) = mime.startsWith(VIDEO_MIME_PREFIX)

/** The MediaStore collection new pictures, or videos if [isVideo], are added to. */
fun mediaCollection(isVideo: Boolean): Uri =
    if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

/**
 * Adds a pending file to [collection], hidden from other apps until [publish]ed.
 *
 * @param relativePath the folder; blank leaves the default one.
 * @param dateTaken the capture date to keep, in milliseconds, if any.
 * @return the new file, or null if MediaStore refused it (e.g. a picture in a video-only folder).
 */
fun ContentResolver.insertPending(
    collection: Uri,
    name: String,
    mime: String,
    relativePath: String,
    dateTaken: Long? = null,
): Uri? = runCatching {
    insert(collection, ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        if (relativePath.isNotBlank()) put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        if (dateTaken != null) put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
        put(MediaStore.MediaColumns.IS_PENDING, PENDING)
    })
}.getOrNull()

/** Makes a file added with [insertPending] visible to other apps. */
fun ContentResolver.publish(uri: Uri) {
    update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, PUBLISHED) }, null, null)
}

/**
 * Writes [input] to [target] in [mode], then syncs it to storage, so a save can't leave an empty file if the
 * phone shuts down right after.
 */
fun ContentResolver.writeSynced(target: Uri, mode: String, input: InputStream) {
    checkNotNull(openFileDescriptor(target, mode)) { "Can't open $target" }.use { descriptor ->
        FileOutputStream(descriptor.fileDescriptor).use { input.copyToSynced(it) }
    }
}

/** Copies this stream to [out], then syncs [out] to storage. */
fun InputStream.copyToSynced(out: FileOutputStream) {
    copyTo(out)
    out.flush()
    out.fd.sync()
}
