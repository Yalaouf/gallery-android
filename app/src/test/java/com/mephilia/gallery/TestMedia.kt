package com.mephilia.gallery

import android.net.Uri
import com.mephilia.gallery.data.MediaItem
import java.time.LocalDateTime
import java.time.ZoneId

/** Builds a media item for tests; only the fields a test cares about need to be given. */
fun media(
    id: Long,
    taken: Long = 0,
    name: String = "IMG_$id.jpg",
    video: Boolean = false,
    size: Long = 1000,
    bucket: Long = 1,
    bucketName: String = "Camera",
    path: String = "DCIM/Camera/",
    modified: Long = taken,
    favorite: Boolean = false,
    uri: Uri = Uri.parse("content://media/external/images/media/$id"),
) = MediaItem(
    id = id, uri = uri, isVideo = video, name = name, mimeType = if (video) "video/mp4" else "image/jpeg",
    dateTaken = taken, dateModified = modified, size = size, width = 100, height = 100, durationMs = 0,
    bucketId = bucket, bucketName = bucketName, relativePath = path, favorite = favorite,
)

/** Milliseconds for a local date and time. */
fun at(year: Int, month: Int, day: Int, hour: Int = 12, zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
