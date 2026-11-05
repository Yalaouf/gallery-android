package com.mephilia.gallery.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.system.Os
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.resume

/** File mode for changing a file in place. */
private const val MODE_READ_WRITE = "rw"

/** Longest wait for Android to read a changed file again. */
private const val SCAN_TIMEOUT_MS = 5_000L

/**
 * Changes the metadata of gallery files in place: their capture date and their location. The file itself is
 * changed, not only Android's index, since Android reads the metadata again whenever a file changes.
 *
 * Both need write access to the file (see MediaAccess): without it, a SecurityException is thrown before anything
 * is written.
 */
class MetadataWriter(private val context: Context) {
    /** Writes through MediaStore. */
    private val resolver = context.contentResolver

    /**
     * Sets the capture date of [item] to [epochMillis]. Photos get the date in their EXIF data, with the timezone
     * the phone has at that date; MP4-family videos in their movie header. Returns false if the file type can't
     * be changed (see [canEditDate]) or the file couldn't be written.
     *
     * Android doesn't let apps change the date in its index directly: it reads it from the file. So the file is
     * scanned again before returning, for the gallery to show the new date at once.
     */
    suspend fun setDateTaken(item: MediaItem, epochMillis: Long): Boolean = withContext(Dispatchers.IO) {
        if (!canEditDate(item)) return@withContext false
        val written = attempt {
            openReadWrite(item).use { descriptor ->
                if (item.isVideo) Mp4Dates.setCreationTime(DescriptorBytes(descriptor.fileDescriptor), epochMillis) > 0
                else writeExifDate(descriptor.fileDescriptor, epochMillis)
            }
        }
        if (written) rescan(item)
        written
    }

    /**
     * Removes every location tag from [item]'s EXIF data. Returns false if the file type can't be changed
     * (see [canRemoveLocation]) or the file couldn't be written.
     */
    suspend fun removeLocation(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        if (!canRemoveLocation(item)) return@withContext false
        attempt {
            openReadWrite(item).use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                clearLocation(exif)
                exif.saveAttributes()
                true
            }
        }
    }

    /** Writes the capture date and its timezone to the EXIF data of the photo open in [descriptor]. */
    private fun writeExifDate(descriptor: FileDescriptor, epochMillis: Long): Boolean {
        val exif = ExifInterface(descriptor)
        setExifDate(exif, epochMillis, ZoneId.systemDefault())
        exif.saveAttributes()
        return true
    }

    /** Asks Android to read [item]'s file again, and waits until it's done (or for [SCAN_TIMEOUT_MS] at most). */
    private suspend fun rescan(item: MediaItem) {
        val path = runCatching {
            resolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: return
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { done ->
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> if (done.isActive) done.resume(Unit) }
            }
        }
    }

    /** [item]'s file, open for reading and writing. */
    private fun openReadWrite(item: MediaItem): ParcelFileDescriptor =
        checkNotNull(resolver.openFileDescriptor(item.uri, MODE_READ_WRITE)) { "Can't open ${item.uri}" }

    /** Runs [block]; a missing permission is passed on, any other failure gives false. */
    private inline fun attempt(block: () -> Boolean): Boolean =
        try {
            block()
        } catch (e: SecurityException) {
            throw e
        } catch (_: Exception) {
            false
        }

    /** The bytes of an open file, read and written at given positions. */
    private class DescriptorBytes(private val descriptor: FileDescriptor) : SeekableBytes {
        override val size: Long get() = Os.fstat(descriptor).st_size

        override fun read(position: Long, length: Int): ByteArray {
            val bytes = ByteArray(length)
            var done = 0
            while (done < length) {
                val read = Os.pread(descriptor, bytes, done, length - done, position + done)
                if (read <= 0) break
                done += read
            }
            return bytes
        }

        override fun write(position: Long, bytes: ByteArray) {
            var done = 0
            while (done < bytes.size) {
                val written = Os.pwrite(descriptor, bytes, done, bytes.size - done, position + done)
                check(written > 0) { "Couldn't write the file" }
                done += written
            }
        }
    }

    companion object {
        /**
         * Sets the capture date in [exif] to [epochMillis], as a local date and time in [zone] with the matching
         * offset. The change is kept in memory until the caller saves the attributes.
         */
        internal fun setExifDate(exif: ExifInterface, epochMillis: Long, zone: ZoneId) {
            val zoned = Instant.ofEpochMilli(epochMillis).atZone(zone)
            val date = ExifFields.dateText(zoned.toLocalDateTime())
            val offset = ExifFields.offsetText(zoned.offset)
            ExifFields.DATE_TAGS.forEach { exif.setAttribute(it, date) }
            ExifFields.OFFSET_TAGS.forEach { exif.setAttribute(it, offset) }
        }

        /** Removes every location tag from [exif]; the change is kept in memory until the caller saves the attributes. */
        internal fun clearLocation(exif: ExifInterface) {
            ExifFields.LOCATION_TAGS.forEach { exif.setAttribute(it, null) }
        }

        /** Whether [item]'s capture date can be changed: JPEG, PNG and WebP photos, and MP4-family videos. */
        fun canEditDate(item: MediaItem): Boolean {
            val mime = item.mimeType.lowercase()
            return if (item.isVideo) mime in Mp4Dates.MIMES else mime in ExifFields.WRITABLE_MIMES
        }

        /** Whether [item]'s location can be removed: JPEG, PNG and WebP photos. */
        fun canRemoveLocation(item: MediaItem): Boolean = !item.isVideo && item.mimeType.lowercase() in ExifFields.WRITABLE_MIMES
    }
}
