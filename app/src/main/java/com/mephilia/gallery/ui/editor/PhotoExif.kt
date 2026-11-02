package com.mephilia.gallery.ui.editor

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Prefix of the EXIF tags holding the location. */
private const val GPS_TAG_PREFIX = "GPS"

/** The EXIF tags carried over to an edited photo: dates, camera and lens, exposure, and location. */
private val COPIED_TAGS = listOf(
    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
    ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
    ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
)

/**
 * Copies the EXIF data of the photo at [from] to the edited JPEG [to], which is [width] × [height].
 *
 * The orientation is reset, since the rotation is already in the pixels, and the size is updated. The location is
 * only copied if [keepLocation]. This is best effort: a failure leaves the photo without EXIF data rather than
 * failing the save.
 */
internal fun copyExif(context: Context, from: Uri, to: File, width: Int, height: Int, keepLocation: Boolean) {
    runCatching {
        val source = context.contentResolver.openInputStream(from)?.use { ExifInterface(it) } ?: return
        val destination = ExifInterface(to)
        for (tag in COPIED_TAGS) {
            if (!keepLocation && tag.startsWith(GPS_TAG_PREFIX)) continue
            source.getAttribute(tag)?.let { destination.setAttribute(tag, it) }
        }
        destination.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        destination.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        destination.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        destination.saveAttributes()
    }
}
