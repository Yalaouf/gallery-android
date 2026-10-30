package com.mephilia.gallery.data

import java.io.Closeable
import java.io.OutputStream

/**
 * Animated GIF89a encoder with no Android dependency.
 *
 * Each frame gets its own 256-color palette, computed by median cut over a 15-bit
 * histogram (5 bits per channel), with 4×4 Bayer ordered dithering to limit banding.
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    loop: Boolean = true,
) : Closeable {

    init {
        require(width in 1..0xFFFF && height in 1..0xFFFF)
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(0x00) // no global color table
        out.write(0x00)
        out.write(0x00)
        // NETSCAPE2.0 extension: loop forever (without it, the GIF plays once)
        if (loop) {
            out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
        }
    }

    private val histogram = IntArray(1 shl 15)
    private val lut = IntArray(1 shl 15)
    private val keys = IntArray(width * height)
    private val indices = ByteArray(width * height)

    /**
     * Adds a frame. [argb] holds width × height ARGB pixels, as from Bitmap.getPixels.
     * [delayCs] is the display time in hundredths of a second.
     */
    fun addFrame(argb: IntArray, delayCs: Int) {
        require(argb.size == width * height)
        val palette = quantize(argb)

        // Frames are complete images, so a frame with transparency must clear the previous one (disposal 2).
        val packed = if (transparentIndex >= 0) (2 shl 2) or 1 else 0
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, packed.toByte()))
        writeShort(delayCs.coerceIn(0, 0xFFFF))
        out.write(byteArrayOf(maxOf(transparentIndex, 0).toByte(), 0x00))

        out.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out.write(0x80 or 0x07)
        out.write(palette)

        Lzw(out).encode(indices)
    }

    override fun close() {
        out.write(0x3B)
        out.flush()
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private var transparentIndex = -1

    /** Builds the palette (768 RGB bytes) and fills [indices]. Pixels with alpha < 128 become transparent. */
    private fun quantize(argb: IntArray): ByteArray {
        histogram.fill(0)
        var hasTransparency = false
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val i = row + x
                val p = argb[i]
                if ((p ushr 24) < 128) {
                    keys[i] = TRANSPARENT_KEY
                    hasTransparency = true
                    continue
                }
                val d = BAYER[(y and 3) * 4 + (x and 3)] / 2 - 4
                val r = (((p shr 16) and 0xFF) + d).coerceIn(0, 255) shr 3
                val g = (((p shr 8) and 0xFF) + d).coerceIn(0, 255) shr 3
                val b = ((p and 0xFF) + d).coerceIn(0, 255) shr 3
                val key = (r shl 10) or (g shl 5) or b
                keys[i] = key
                histogram[key]++
            }
        }

        var n = 0
        for (k in histogram.indices) if (histogram[k] > 0) n++
        val colors = IntArray(n)
        n = 0
        for (k in histogram.indices) if (histogram[k] > 0) colors[n++] = k

        // Median cut: always split the most populated box that still holds several colors
        val boxes = ArrayList<IntArray>() // [start, end) ranges in colors
        if (colors.isNotEmpty()) boxes += intArrayOf(0, colors.size)
        // One palette slot is kept for the transparent color
        val maxColors = if (hasTransparency) 255 else 256
        while (boxes.size < maxColors) {
            var best = -1
            var bestCount = -1L
            for ((bi, box) in boxes.withIndex()) {
                if (box[1] - box[0] < 2) continue
                var c = 0L
                for (j in box[0] until box[1]) c += histogram[colors[j]]
                if (c > bestCount) { bestCount = c; best = bi }
            }
            if (best < 0) break
            val (start, end) = boxes[best].let { it[0] to it[1] }
            val shift = widestChannel(colors, start, end)
            sortRange(colors, start, end, shift)
            var acc = 0L
            var cut = start + 1
            for (j in start until end) {
                acc += histogram[colors[j]]
                if (acc * 2 >= bestCount) { cut = (j + 1).coerceIn(start + 1, end - 1); break }
            }
            boxes[best] = intArrayOf(start, cut)
            boxes += intArrayOf(cut, end)
        }

        val palette = ByteArray(768)
        for ((bi, box) in boxes.withIndex()) {
            var sr = 0L; var sg = 0L; var sb = 0L; var sc = 0L
            for (j in box[0] until box[1]) {
                val k = colors[j]
                val c = histogram[k].toLong()
                sr += ((k shr 10) and 31) * c; sg += ((k shr 5) and 31) * c; sb += (k and 31) * c; sc += c
                lut[k] = bi
            }
            palette[bi * 3] = ((sr * 8 + sc * 4) / sc).toInt().coerceIn(0, 255).toByte()
            palette[bi * 3 + 1] = ((sg * 8 + sc * 4) / sc).toInt().coerceIn(0, 255).toByte()
            palette[bi * 3 + 2] = ((sb * 8 + sc * 4) / sc).toInt().coerceIn(0, 255).toByte()
        }
        transparentIndex = if (hasTransparency) boxes.size else -1
        for (i in keys.indices) {
            val k = keys[i]
            indices[i] = (if (k == TRANSPARENT_KEY) transparentIndex else lut[k]).toByte()
        }
        return palette
    }

    private fun widestChannel(colors: IntArray, start: Int, end: Int): Int {
        var best = 0
        var bestRange = -1
        for (shift in intArrayOf(10, 5, 0)) {
            var lo = 31; var hi = 0
            for (j in start until end) {
                val v = (colors[j] shr shift) and 31
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            if (hi - lo > bestRange) { bestRange = hi - lo; best = shift }
        }
        return best
    }

    private fun sortRange(colors: IntArray, start: Int, end: Int, shift: Int) {
        val sorted = colors.copyOfRange(start, end).sortedBy { (it shr shift) and 31 }
        for (j in sorted.indices) colors[start + j] = sorted[j]
    }

    /** GIF LZW compression with variable code size (9 to 12 bits). */
    private class Lzw(private val out: OutputStream) {
        private val block = ByteArray(255)
        private var blockLen = 0
        private var bitBuf = 0
        private var bitCount = 0
        private var codeSize = MIN_CODE_SIZE + 1
        private var next = EOI + 1

        fun encode(data: ByteArray) {
            out.write(MIN_CODE_SIZE)
            val dict = HashMap<Int, Int>(8192)
            emit(CLEAR)
            if (data.isNotEmpty()) {
                var prefix = data[0].toInt() and 0xFF
                for (i in 1 until data.size) {
                    val k = data[i].toInt() and 0xFF
                    val key = (prefix shl 8) or k
                    val code = dict[key]
                    if (code != null) {
                        prefix = code
                        continue
                    }
                    emit(prefix)
                    if (next < MAX_CODES) {
                        dict[key] = next++
                    } else {
                        emit(CLEAR)
                        dict.clear()
                    }
                    prefix = k
                }
                emit(prefix)
            }
            emit(EOI)
            if (bitCount > 0) byte(bitBuf and 0xFF)
            flushBlock()
            out.write(0x00) // block terminator
        }

        /**
         * Like the reference encoders, the code size grows right after emitting a code
         * once the next free code no longer fits. Decoders rely on this exact timing.
         */
        private fun emit(code: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                byte(bitBuf and 0xFF)
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
            if (code == CLEAR) {
                codeSize = MIN_CODE_SIZE + 1
                next = EOI + 1
            } else if (codeSize < 12 && next > (1 shl codeSize) - 1) {
                codeSize++
            }
        }

        private fun byte(b: Int) {
            block[blockLen++] = b.toByte()
            if (blockLen == 255) flushBlock()
        }

        private fun flushBlock() {
            if (blockLen == 0) return
            out.write(blockLen)
            out.write(block, 0, blockLen)
            blockLen = 0
        }

        companion object {
            const val MIN_CODE_SIZE = 8
            const val CLEAR = 256
            const val EOI = 257
            const val MAX_CODES = 4096
        }
    }

    companion object {
        private const val TRANSPARENT_KEY = -1
        private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    }
}
