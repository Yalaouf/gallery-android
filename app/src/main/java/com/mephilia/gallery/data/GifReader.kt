package com.mephilia.gallery.data

/** Mask of the low byte of an int. */
private const val BYTE_MASK = 0xFF

/** Bits in a byte. */
private const val BITS_PER_BYTE = 8

/**
 * Reads the bytes of a GIF file in order. Reading past the end returns zeros instead of failing,
 * so a truncated file decodes as far as it goes.
 */
internal class GifReader(private val data: ByteArray) {
    /** Position of the next byte to read. */
    var pos = 0

    /** Number of bytes in the file. */
    val size: Int get() = data.size

    /** Whether every byte has been read. */
    val atEnd: Boolean get() = pos >= data.size

    /** The next byte, unsigned. */
    fun u8(): Int = if (pos < data.size) data[pos++].toInt() and BYTE_MASK else 0

    /** The next two bytes, as a little-endian unsigned 16-bit value. */
    fun u16(): Int = u8() or (u8() shl BITS_PER_BYTE)

    /** The byte at [index], unsigned, without moving; 0 past the end. */
    fun peek(index: Int): Int = if (index in data.indices) data[index].toInt() and BYTE_MASK else 0

    /** [length] bytes from [start] as ASCII text, cut short at the end of the data. */
    fun ascii(start: Int, length: Int): String =
        if (start >= data.size) "" else String(data, start, minOf(length, data.size - start), Charsets.US_ASCII)

    /** Moves past [count] bytes, without going beyond the end. */
    fun skip(count: Int) {
        pos = minOf(data.size, pos + count)
    }

    /** Moves past a sequence of data sub-blocks, up to and including its terminator. */
    fun skipSubBlocks() {
        while (!atEnd) {
            val size = u8()
            if (size == GifFormat.BLOCK_TERMINATOR) return
            pos += size
        }
    }
}
