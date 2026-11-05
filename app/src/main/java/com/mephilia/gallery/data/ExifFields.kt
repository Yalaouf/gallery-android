package com.mephilia.gallery.data

import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Seconds in a minute. */
private const val SECONDS_PER_MINUTE = 60

/** Seconds in an hour. */
private const val SECONDS_PER_HOUR = 3600

/** The EXIF fields the app reads and writes, and how their values are formatted. */
internal object ExifFields {
    /** Photo types whose EXIF data can be rewritten in place. HEIC, AVIF and others can only be read. */
    val WRITABLE_MIMES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    /** Every EXIF tag that can hold a location, including XMP metadata, which can repeat it. */
    val LOCATION_TAGS = listOf(
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

    /** The tags holding the capture date: when the photo was taken, digitized, and last changed. */
    val DATE_TAGS = listOf(ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME)

    /**
     * The tags holding the timezone of [DATE_TAGS]. Android only trusts a photo's EXIF date when it has a
     * timezone (or when the file's own date is within a day of it), so it's always written with the date.
     */
    val OFFSET_TAGS = listOf(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED, ExifInterface.TAG_OFFSET_TIME)

    /** How EXIF writes a local date and time. */
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** A local date and time as EXIF writes it, e.g. "2026:11:05 14:30:00". */
    fun dateText(local: LocalDateTime): String = local.format(DATE_FORMAT)

    /** A timezone offset as EXIF writes it, always with its sign and minutes: "+01:00", "-05:30", "+00:00". */
    fun offsetText(offset: ZoneOffset): String {
        val seconds = offset.totalSeconds
        val sign = if (seconds < 0) '-' else '+'
        val hours = abs(seconds) / SECONDS_PER_HOUR
        val minutes = abs(seconds) % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        return "%c%02d:%02d".format(sign, hours, minutes)
    }
}
