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
import java.util.concurrent.TimeUnit

/** Folder of the cache holding the copies; the only one the FileProvider exposes. */
private const val SHARED_DIR = "shared"

/** Suffix of the FileProvider's authority, after the package name. */
private const val FILE_PROVIDER_SUFFIX = ".files"

/** How long copies are kept: the app they were sent to may still be reading them. */
private val COPY_LIFETIME_MS = TimeUnit.HOURS.toMillis(1)

/** Quality of the JPEG made from formats that can't be edited in place. */
private const val CONVERTED_JPEG_QUALITY = 95

/** Name of a copy whose original has no name. */
private const val DEFAULT_NAME = "photo.jpg"

/** Extension of a converted copy. */
private const val JPEG_EXTENSION = "jpg"

/**
 * Copies of photos without their location, for sharing. JPEG, PNG and WebP keep everything else
 * (date, camera…); other formats (HEIC, AVIF…) are converted to JPEG, without any metadata.
 * Videos are shared as they are.
 */
object ShareCopies {
    /** Every EXIF tag that can hold a location, including XMP metadata, which can repeat it. */
    private val LOCATION_TAGS = listOf(
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
        ExifInterface.TAG_XMP,
    )

    /** Types whose metadata can be edited in place. */
    private val EDITABLE_MIMES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    /**
     * The URIs to share, in the same order as [items]. A photo that couldn't be cleaned gives null: it must not
     * be shared, since the original still has its location. Copies older than an hour are removed first.
     */
    suspend fun prepare(context: Context, items: List<MediaItem>): List<Uri?> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val expired = System.currentTimeMillis() - COPY_LIFETIME_MS
        dir.listFiles()?.filter { it.lastModified() < expired }?.forEach { it.deleteRecursively() }
        val batch = File(dir, System.nanoTime().toString()).apply { mkdirs() }
        items.map { item ->
            if (item.isVideo) item.uri else runCatching { stripped(context, item, batch) }.getOrNull()
        }
    }

    /** Writes a copy of [item] without its location in [dir], and returns a URI other apps can read it from. */
    internal fun stripped(context: Context, item: MediaItem, dir: File): Uri {
        val out = if (item.mimeType.lowercase() in EDITABLE_MIMES) {
            File(dir, item.name.ifBlank { DEFAULT_NAME }).also { copyWithoutLocation(context, item, it) }
        } else {
            File(dir, item.name.substringBeforeLast('.', item.name) + ".$JPEG_EXTENSION").also { convertToJpeg(context, item, it) }
        }
        return FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, out)
    }

    /** Copies [item] to [out] as it is, then removes the location tags. */
    private fun copyWithoutLocation(context: Context, item: MediaItem, out: File) {
        checkNotNull(context.contentResolver.openInputStream(item.uri)).use { input -> out.outputStream().use { input.copyTo(it) } }
        val exif = ExifInterface(out)
        LOCATION_TAGS.forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
    }

    /** Re-encodes [item] to a JPEG [out], which keeps no metadata at all. */
    private fun convertToJpeg(context: Context, item: MediaItem, out: File) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, CONVERTED_JPEG_QUALITY, it) }
        bitmap.recycle()
    }
}
