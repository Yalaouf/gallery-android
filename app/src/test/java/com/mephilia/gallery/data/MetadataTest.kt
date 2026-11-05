package com.mephilia.gallery.data

import androidx.exifinterface.media.ExifInterface
import com.mephilia.gallery.media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Changing the capture date and removing the location write into the user's own files: they must be exact. */
@RunWith(RobolectricTestRunner::class)
class MetadataTest {
    /** A copy of a test resource in a temporary file, safe to change. */
    private fun copyOf(name: String): File {
        val file = File.createTempFile("metadata", name)
        file.writeBytes(checkNotNull(javaClass.classLoader?.getResourceAsStream("metadata/$name")).use { it.readBytes() })
        return file
    }

    /** A file opened for reading and writing at given positions. */
    private class FileBytes(private val file: RandomAccessFile) : SeekableBytes {
        override val size get() = file.length()
        override fun read(position: Long, length: Int) = ByteArray(length).also { file.seek(position); file.readFully(it) }
        override fun write(position: Long, bytes: ByteArray) {
            file.seek(position)
            file.write(bytes)
        }
    }

    private fun <T> withBytes(file: File, block: (SeekableBytes) -> T): T = RandomAccessFile(file, "rw").use { block(FileBytes(it)) }

    private val millis = ZonedDateTime.of(2024, 7, 14, 9, 30, 0, 0, ZoneId.of("Europe/Paris")).toInstant().toEpochMilli()

    @Test
    fun changesTheCreationDateOfARealMp4InPlace() {
        val video = copyOf("tiny.mp4")
        val before = video.length()
        withBytes(video) { file ->
            assertEquals(ZonedDateTime.of(2020, 5, 11, 10, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(), Mp4Dates.creationTime(file))
            assertTrue(Mp4Dates.setCreationTime(file, millis) >= 1)
            assertEquals(millis, Mp4Dates.creationTime(file))
        }
        assertEquals("nothing moves in the file", before, video.length())
    }

    /** A box: 32-bit size, type, then [content]. */
    private fun box(type: String, content: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + content.size).putInt(8 + content.size).put(type.toByteArray()).put(content).array()

    /** A header box of [version] with creation and modification times of [seconds] since 1904. */
    private fun timed(type: String, version: Int, seconds: Long): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(byteArrayOf(version.toByte(), 0, 0, 0))
        repeat(2) {
            content.write(if (version == 1) ByteBuffer.allocate(8).putLong(seconds).array() else ByteBuffer.allocate(4).putInt(seconds.toInt()).array())
        }
        content.write(ByteArray(80))
        return box(type, content.toByteArray())
    }

    @Test
    fun handlesLongTimesLargeBoxesAndEveryTrack() {
        val largeMdat = ByteBuffer.allocate(16 + 4).putInt(1).put("mdat".toByteArray()).putLong(16 + 4).putInt(0).array()
        val trak = box("trak", timed("tkhd", 0, 100) + box("mdia", timed("mdhd", 1, 100)))
        val bytes = box("ftyp", "isom".toByteArray()) + largeMdat + box("moov", timed("mvhd", 1, 100) + trak)
        val video = File.createTempFile("metadata", ".mp4").apply { writeBytes(bytes) }
        withBytes(video) { file ->
            assertEquals("movie, track and media dates", 3, Mp4Dates.setCreationTime(file, millis))
            assertEquals(millis, Mp4Dates.creationTime(file))
        }
    }

    @Test
    fun leavesFilesThatArentMp4Alone() {
        val notVideo = File.createTempFile("metadata", ".bin").apply { writeBytes(ByteArray(64) { 0x7F }) }
        val before = notVideo.readBytes()
        withBytes(notVideo) { file ->
            assertEquals(0, Mp4Dates.setCreationTime(file, millis))
            assertNull(Mp4Dates.creationTime(file))
        }
        assertTrue(before.contentEquals(notVideo.readBytes()))
    }

    @Test
    fun writesThePhotoDateWithItsTimezone() {
        val photo = copyOf("located.jpg")
        ExifInterface(photo).apply {
            MetadataWriter.setExifDate(this, millis, ZoneId.of("Europe/Paris"))
            saveAttributes()
        }
        val exif = ExifInterface(photo)
        assertEquals("2024:07:14 09:30:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("+02:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        assertEquals(millis, exif.dateTimeOriginal)
        assertNotNull("the location is kept", exif.latLong)
    }

    @Test
    fun removesTheLocationAndKeepsTheDate() {
        val photo = copyOf("located.jpg")
        assertNotNull(ExifInterface(photo).latLong)
        ExifInterface(photo).apply {
            MetadataWriter.clearLocation(this)
            saveAttributes()
        }
        val exif = ExifInterface(photo)
        assertNull(exif.latLong)
        assertEquals("2019:01:02 03:04:05", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }

    @Test
    fun timezonesAlwaysHaveASignAndMinutes() {
        assertEquals("+00:00", ExifFields.offsetText(ZoneOffset.UTC))
        assertEquals("+01:00", ExifFields.offsetText(ZoneOffset.ofHours(1)))
        assertEquals("-05:30", ExifFields.offsetText(ZoneOffset.ofHoursMinutes(-5, -30)))
    }

    @Test
    fun onlySupportedFileTypesAreOffered() {
        assertTrue(MetadataWriter.canEditDate(media(1, mime = "image/jpeg")))
        assertTrue(MetadataWriter.canEditDate(media(2, video = true, mime = "video/mp4")))
        assertFalse(MetadataWriter.canEditDate(media(3, mime = "image/heic")))
        assertFalse(MetadataWriter.canEditDate(media(4, video = true, mime = "video/webm")))
        assertTrue(MetadataWriter.canRemoveLocation(media(5, mime = "image/png")))
        assertFalse(MetadataWriter.canRemoveLocation(media(6, video = true, mime = "video/mp4")))
    }

    @Test
    fun newDatesShiftEveryItemOrSetThemAll() {
        val a = media(1, taken = 1_000)
        val b = media(2, taken = 5_000)
        assertEquals(mapOf(1L to 10_000L, 2L to 14_000L), MediaQueries.newDates(listOf(b, a), 10_000, keepGaps = true))
        assertEquals(mapOf(1L to 10_000L, 2L to 10_000L), MediaQueries.newDates(listOf(b, a), 10_000, keepGaps = false))
        assertEquals(emptyMap<Long, Long>(), MediaQueries.newDates(emptyList(), 10_000, keepGaps = true))
    }
}
