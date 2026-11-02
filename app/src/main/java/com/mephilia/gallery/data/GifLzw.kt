package com.mephilia.gallery.data

import java.io.OutputStream

/** Bits in a byte. */
private const val BITS_PER_BYTE = 8

/** Mask of the low byte of an int. */
private const val BYTE_MASK = 0xFF

/** Code size of 8-bit color indices, written before the compressed data. */
private const val MIN_CODE_SIZE = 8

/** Clears the dictionary: the first code after the literal ones. */
private const val CLEAR_CODE = 1 shl MIN_CODE_SIZE

/** Ends the compressed data. */
private const val END_OF_INFORMATION = CLEAR_CODE + 1

/** Initial capacity of the encoder's dictionary: room for every code without growing. */
private const val DICTIONARY_CAPACITY = 2 * GifFormat.MAX_LZW_CODES

/**
 * GIF's LZW compression of 8-bit color indices, with a variable code size (9 to 12 bits),
 * written as data sub-blocks to [out].
 */
internal class GifLzwEncoder(private val out: OutputStream) {
    /** The data sub-block being filled. */
    private val block = ByteArray(GifFormat.MAX_SUB_BLOCK_SIZE)

    /** Bytes in the current sub-block. */
    private var blockLength = 0

    /** Bits waiting to be written, lowest first. */
    private var bitBuffer = 0

    /** Number of bits in [bitBuffer]. */
    private var bitCount = 0

    /** Current code size, in bits. */
    private var codeSize = MIN_CODE_SIZE + 1

    /** The code the next dictionary entry gets. */
    private var nextCode = END_OF_INFORMATION + 1

    /** Compresses [data] (one color index per byte) and writes it, followed by the block terminator. */
    fun encode(data: ByteArray) {
        out.write(MIN_CODE_SIZE)
        val dictionary = HashMap<Int, Int>(DICTIONARY_CAPACITY)
        emit(CLEAR_CODE)
        if (data.isNotEmpty()) {
            var prefix = data[0].toInt() and BYTE_MASK
            for (i in 1 until data.size) {
                val symbol = data[i].toInt() and BYTE_MASK
                val key = (prefix shl BITS_PER_BYTE) or symbol
                val code = dictionary[key]
                if (code != null) {
                    prefix = code
                    continue
                }
                emit(prefix)
                if (nextCode < GifFormat.MAX_LZW_CODES) {
                    dictionary[key] = nextCode++
                } else {
                    emit(CLEAR_CODE)
                    dictionary.clear()
                }
                prefix = symbol
            }
            emit(prefix)
        }
        emit(END_OF_INFORMATION)
        if (bitCount > 0) writeByte(bitBuffer and BYTE_MASK)
        flushBlock()
        out.write(GifFormat.BLOCK_TERMINATOR)
    }

    /**
     * Appends [code] to the bit stream. Like the reference encoders, the code size grows right after emitting
     * a code once the next free code no longer fits; decoders rely on this exact timing.
     */
    private fun emit(code: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += codeSize
        while (bitCount >= BITS_PER_BYTE) {
            writeByte(bitBuffer and BYTE_MASK)
            bitBuffer = bitBuffer ushr BITS_PER_BYTE
            bitCount -= BITS_PER_BYTE
        }
        if (code == CLEAR_CODE) {
            codeSize = MIN_CODE_SIZE + 1
            nextCode = END_OF_INFORMATION + 1
        } else if (codeSize < GifFormat.MAX_LZW_CODE_SIZE && nextCode > (1 shl codeSize) - 1) {
            codeSize++
        }
    }

    /** Adds a byte to the current sub-block, writing the sub-block out once it's full. */
    private fun writeByte(byte: Int) {
        block[blockLength++] = byte.toByte()
        if (blockLength == GifFormat.MAX_SUB_BLOCK_SIZE) flushBlock()
    }

    /** Writes the current sub-block, preceded by its size. */
    private fun flushBlock() {
        if (blockLength == 0) return
        out.write(blockLength)
        out.write(block, 0, blockLength)
        blockLength = 0
    }
}

/**
 * Decompresses the LZW data at [reader]'s position into [count] color indices.
 *
 * A code that isn't in the dictionary yet (the "KwKwK" case) stands for the previous string followed by its own
 * first index. Damaged or truncated data stops the decoding early; the remaining indices stay 0.
 * The reader is left after the image data.
 */
internal fun decodeGifLzw(reader: GifReader, count: Int): ByteArray {
    val minCodeSize = reader.u8()
    val out = ByteArray(count)
    val clear = 1 shl minCodeSize
    val endOfInformation = clear + 1
    val prefix = ShortArray(GifFormat.MAX_LZW_CODES)
    val suffix = ByteArray(GifFormat.MAX_LZW_CODES)
    val stack = ByteArray(GifFormat.MAX_LZW_CODES + 1)
    for (i in 0 until clear) suffix[i] = i.toByte()

    var codeSize = minCodeSize + 1
    var nextCode = clear + 2
    var previous = -1
    var first = 0
    var outPos = 0
    var bits = 0
    var bitCount = 0
    var blockLeft = 0

    decoding@ while (outPos < count) {
        while (bitCount < codeSize) {
            if (blockLeft == 0) {
                if (reader.atEnd) break@decoding
                blockLeft = reader.u8()
                if (blockLeft == GifFormat.BLOCK_TERMINATOR) break@decoding
            }
            if (reader.atEnd) break@decoding
            bits = bits or (reader.u8() shl bitCount)
            bitCount += BITS_PER_BYTE
            blockLeft--
        }
        val code = bits and ((1 shl codeSize) - 1)
        bits = bits ushr codeSize
        bitCount -= codeSize

        if (code == clear) {
            codeSize = minCodeSize + 1
            nextCode = clear + 2
            previous = -1
            continue
        }
        if (code == endOfInformation) break

        if (previous == -1) {
            if (code >= clear) break
            out[outPos++] = suffix[code]
            previous = code
            first = code
            continue
        }

        var stackSize = 0
        var current = code
        if (current >= nextCode) {
            stack[stackSize++] = first.toByte()
            current = previous
        }
        while (current >= clear) {
            stack[stackSize++] = suffix[current]
            current = prefix[current].toInt()
        }
        first = suffix[current].toInt() and BYTE_MASK
        stack[stackSize++] = first.toByte()
        while (stackSize > 0 && outPos < count) out[outPos++] = stack[--stackSize]

        if (nextCode < GifFormat.MAX_LZW_CODES) {
            prefix[nextCode] = previous.toShort()
            suffix[nextCode] = first.toByte()
            nextCode++
            if (nextCode == (1 shl codeSize) && codeSize < GifFormat.MAX_LZW_CODE_SIZE) codeSize++
        }
        previous = code
    }
    reader.skip(blockLeft)
    reader.skipSubBlocks()
    return out
}
