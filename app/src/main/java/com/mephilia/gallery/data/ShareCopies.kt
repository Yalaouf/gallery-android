package com.mephilia.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies of photos without their location, for sharing. JPEG, PNG and WebP keep everything else
 * (date, camera…); other formats (HEIC, AVIF…) are converted to JPEG. Videos are shared as they are.
 */
object ShareCopies {
    private val gpsTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF, ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_VERSION_ID, ExifInterface.TAG_GPS_MAP_DATUM,
        // XMP metadata can repeat the location
        ExifInterface.TAG_XMP,
    )
    private val editable = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    /** Returns the URIs to share, in the same order as [items]. */
    suspend fun prepare(context: Context, items: List<MediaItem>): List<Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Earlier copies may still be read by the app they were sent to: only remove old ones
        val old = System.currentTimeMillis() - 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.deleteRecursively() }
        val batch = File(dir, System.nanoTime().toString()).apply { mkdirs() }
        items.map { item ->
            if (item.isVideo) return@map item.uri
            runCatching { stripped(context, item, batch) }.getOrNull() ?: item.uri
        }
    }

    private fun stripped(context: Context, item: MediaItem, dir: File): Uri {
        val mime = item.mimeType.lowercase()
        val out: File
        if (mime in editable) {
            out = File(dir, item.name.ifBlank { "photo.jpg" })
            context.contentResolver.openInputStream(item.uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            val exif = ExifInterface(out)
            gpsTags.forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()
        } else {
            // Formats that can't be edited in place: re-encode, without any metadata
            out = File(dir, item.name.substringBeforeLast('.', item.name) + ".jpg")
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", out)
    }
}
