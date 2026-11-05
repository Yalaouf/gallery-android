package com.mephilia.gallery.data

import java.nio.ByteBuffer

/** Seconds between the MP4 epoch (1904-01-01 UTC) and the Unix epoch (1970-01-01 UTC). */
private const val MP4_EPOCH_OFFSET_SECONDS = 2_082_844_800L

/** Milliseconds in a second. */
private const val MILLIS_PER_SECOND = 1000L

/** Size of a box header: 32-bit size, then 4-character type. */
private const val HEADER_SIZE = 8

/** Size of the 64-bit size that follows the header when the 32-bit size is 1. */
private const val LARGE_SIZE_BYTES = 8

/** A 32-bit size of 1 means the real size follows as 64 bits. */
private const val SIZE_IS_LARGE = 1L

/** A 32-bit size of 0 means the box runs to the end of the file. */
private const val SIZE_TO_END = 0L

/** Version and flags before the times of a header box. */
private const val VERSION_AND_FLAGS_BYTES = 4

/** Version of the header boxes whose times are 64 bits; version 0 uses 32 bits. */
private const val VERSION_LONG_TIMES = 1

/** Boxes holding other boxes on the way to the header boxes. */
private val CONTAINERS = setOf("moov", "trak", "mdia")

/** Header boxes with a creation time: the movie (mvhd), each track (tkhd), and each track's media (mdhd). */
private val TIMED_BOXES = setOf("mvhd", "tkhd", "mdhd")

/** Deepest nesting followed, which stops a malformed file from looping. */
private const val MAX_DEPTH = 8

/** Mask of an unsigned 32-bit value held in a Long. */
private const val UINT32_MASK = 0xFFFF_FFFFL

/** Bytes of a file read and written at given positions, without loading the whole file. */
interface SeekableBytes {
    /** Size of the file, in bytes. */
    val size: Long

    /** [length] bytes from [position]. */
    fun read(position: Long, length: Int): ByteArray

    /** Writes [bytes] at [position]. */
    fun write(position: Long, bytes: ByteArray)
}

/**
 * Reads and changes the creation date of MP4-family videos (MP4, MOV, 3GP) in place. The date is a fixed-size
 * field of the movie header (mvhd), and also of each track (tkhd, mdhd); Android reads the movie's.
 * Nothing else in the file moves, so no re-encoding is needed.
 */
object Mp4Dates {
    /** Video types that use the MP4 box structure. */
    val MIMES = setOf("video/mp4", "video/quicktime", "video/3gpp", "video/3gpp2")

    /** The movie's creation date, in milliseconds since 1970, or null if the file has no movie header. */
    fun creationTime(file: SeekableBytes): Long? {
        var result: Long? = null
        walk(file, 0, file.size, 0) { type, timeOffset, longTimes ->
            if (type == "mvhd" && result == null) {
                val seconds = if (longTimes) ByteBuffer.wrap(file.read(timeOffset, Long.SIZE_BYTES)).long
                else ByteBuffer.wrap(file.read(timeOffset, Int.SIZE_BYTES)).int.toLong() and UINT32_MASK
                result = (seconds - MP4_EPOCH_OFFSET_SECONDS) * MILLIS_PER_SECOND
            }
        }
        return result
    }

    /**
     * Sets the creation date of the movie and its tracks to [epochMillis]. Returns how many dates were written:
     * 0 if the file isn't an MP4-family video with a movie header.
     */
    fun setCreationTime(file: SeekableBytes, epochMillis: Long): Int {
        val seconds = epochMillis / MILLIS_PER_SECOND + MP4_EPOCH_OFFSET_SECONDS
        var written = 0
        walk(file, 0, file.size, 0) { _, timeOffset, longTimes ->
            val bytes = if (longTimes) ByteBuffer.allocate(Long.SIZE_BYTES).putLong(seconds).array()
            else ByteBuffer.allocate(Int.SIZE_BYTES).putInt((seconds and UINT32_MASK).toInt()).array()
            file.write(timeOffset, bytes)
            written++
        }
        return written
    }

    /**
     * Walks the boxes between [start] and [end], descending into [CONTAINERS], and calls [onTimed] for each of
     * [TIMED_BOXES] with its type, the position of its creation time and whether the time is 64 bits.
     * Stops at the first malformed box.
     */
    private fun walk(file: SeekableBytes, start: Long, end: Long, depth: Int, onTimed: (String, Long, Boolean) -> Unit) {
        if (depth > MAX_DEPTH) return
        var position = start
        while (position + HEADER_SIZE <= end) {
            val header = ByteBuffer.wrap(file.read(position, HEADER_SIZE))
            var boxSize = header.int.toLong() and UINT32_MASK
            val type = String(ByteArray(Int.SIZE_BYTES).also { header.get(it) }, Charsets.US_ASCII)
            var headerSize = HEADER_SIZE.toLong()
            when (boxSize) {
                SIZE_IS_LARGE -> {
                    if (position + HEADER_SIZE + LARGE_SIZE_BYTES > end) return
                    boxSize = ByteBuffer.wrap(file.read(position + HEADER_SIZE, LARGE_SIZE_BYTES)).long
                    headerSize += LARGE_SIZE_BYTES
                }
                SIZE_TO_END -> boxSize = end - position
            }
            if (boxSize < headerSize || position + boxSize > end) return
            val content = position + headerSize
            when (type) {
                in CONTAINERS -> walk(file, content, position + boxSize, depth + 1, onTimed)
                in TIMED_BOXES -> if (content + VERSION_AND_FLAGS_BYTES + Long.SIZE_BYTES <= position + boxSize) {
                    val longTimes = file.read(content, 1)[0].toInt() == VERSION_LONG_TIMES
                    onTimed(type, content + VERSION_AND_FLAGS_BYTES, longTimes)
                }
            }
            position += boxSize
        }
    }
}
