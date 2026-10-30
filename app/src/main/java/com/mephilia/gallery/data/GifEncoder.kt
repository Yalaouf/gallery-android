package com.mephilia.gallery.data

import java.io.Closeable
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Animated GIF89a encoder with no Android dependency.
 *
 * Each frame gets its own 256-color palette: median cut over an 18-bit histogram (6 bits per
 * channel), refined by one k-means pass. Pixels are then mapped with serpentine Floyd–Steinberg
 * error diffusion, which hides banding in gradients far better than ordered dithering.
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

    private val histogram = IntArray(1 shl 18)
    /** Nearest palette entry for each 18-bit color, filled lazily during dithering (-1 = unknown). */
    private val nearestCache = IntArray(1 shl 18)
    private val indices = ByteArray(width * height)
    private val palR = FloatArray(256)
    private val palG = FloatArray(256)
    private val palB = FloatArray(256)
    private var paletteSize = 0
    // Diffused error for the current and next row, 3 channels per pixel, with a 1-pixel border on each side
    private var errCur = FloatArray((width + 2) * 3)
    private var errNext = FloatArray((width + 2) * 3)

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
        for (p in argb) {
            if ((p ushr 24) < 128) {
                hasTransparency = true
                continue
            }
            histogram[key6((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)]++
        }
        var n = 0
        for (c in histogram) if (c > 0) n++
        val colors = IntArray(n)
        n = 0
        for (k in histogram.indices) if (histogram[k] > 0) colors[n++] = k

        buildPalette(colors, if (hasTransparency) 255 else 256)
        refinePalette(colors)
        transparentIndex = if (hasTransparency) paletteSize else -1

        val palette = ByteArray(768)
        for (i in 0 until paletteSize) {
            palette[i * 3] = palR[i].roundToInt().coerceIn(0, 255).toByte()
            palette[i * 3 + 1] = palG[i].roundToInt().coerceIn(0, 255).toByte()
            palette[i * 3 + 2] = palB[i].roundToInt().coerceIn(0, 255).toByte()
            // Dithering must aim at the colors actually written, not the unrounded means
            palR[i] = (palette[i * 3].toInt() and 0xFF).toFloat()
            palG[i] = (palette[i * 3 + 1].toInt() and 0xFF).toFloat()
            palB[i] = (palette[i * 3 + 2].toInt() and 0xFF).toFloat()
        }
        dither(argb)
        return palette
    }

    /** Median cut: always split the most populated box that still holds several colors. */
    private fun buildPalette(colors: IntArray, maxColors: Int) {
        val boxes = ArrayList<IntArray>() // [start, end) ranges in colors
        if (colors.isNotEmpty()) boxes += intArrayOf(0, colors.size)
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
            val start = boxes[best][0]
            val end = boxes[best][1]
            sortRange(colors, start, end, widestChannel(colors, start, end))
            var acc = 0L
            var cut = start + 1
            for (j in start until end) {
                acc += histogram[colors[j]]
                if (acc * 2 >= bestCount) { cut = (j + 1).coerceIn(start + 1, end - 1); break }
            }
            boxes[best] = intArrayOf(start, cut)
            boxes += intArrayOf(cut, end)
        }
        paletteSize = boxes.size
        for ((bi, box) in boxes.withIndex()) {
            var sr = 0.0; var sg = 0.0; var sb = 0.0; var sc = 0.0
            for (j in box[0] until box[1]) {
                val k = colors[j]
                val c = histogram[k].toDouble()
                sr += center6((k shr 12) and 63) * c
                sg += center6((k shr 6) and 63) * c
                sb += center6(k and 63) * c
                sc += c
            }
            palR[bi] = (sr / sc).toFloat(); palG[bi] = (sg / sc).toFloat(); palB[bi] = (sb / sc).toFloat()
        }
    }

    /** One k-means pass: moves each palette color to the mean of the colors that are nearest to it. */
    private fun refinePalette(colors: IntArray) {
        if (paletteSize < 2) return
        val sr = DoubleArray(paletteSize); val sg = DoubleArray(paletteSize)
        val sb = DoubleArray(paletteSize); val sc = DoubleArray(paletteSize)
        for (k in colors) {
            val r = center6((k shr 12) and 63); val g = center6((k shr 6) and 63); val b = center6(k and 63)
            val i = nearest(r, g, b)
            val c = histogram[k].toDouble()
            sr[i] += r * c; sg[i] += g * c; sb[i] += b * c; sc[i] += c
        }
        for (i in 0 until paletteSize) {
            if (sc[i] > 0) {
                palR[i] = (sr[i] / sc[i]).toFloat(); palG[i] = (sg[i] / sc[i]).toFloat(); palB[i] = (sb[i] / sc[i]).toFloat()
            }
        }
    }

    /** Serpentine Floyd–Steinberg error diffusion into [indices]. */
    private fun dither(argb: IntArray) {
        nearestCache.fill(-1)
        errCur.fill(0f)
        errNext.fill(0f)
        for (y in 0 until height) {
            val leftToRight = y % 2 == 0
            val dir = if (leftToRight) 1 else -1
            var x = if (leftToRight) 0 else width - 1
            repeat(width) {
                val i = y * width + x
                val p = argb[i]
                if ((p ushr 24) < 128) {
                    indices[i] = transparentIndex.toByte()
                } else {
                    val e = (x + 1) * 3
                    val r = (((p shr 16) and 0xFF) + errCur[e]).coerceIn(0f, 255f)
                    val g = (((p shr 8) and 0xFF) + errCur[e + 1]).coerceIn(0f, 255f)
                    val b = ((p and 0xFF) + errCur[e + 2]).coerceIn(0f, 255f)
                    val key = key6(r.toInt(), g.toInt(), b.toInt())
                    var idx = nearestCache[key]
                    if (idx < 0) {
                        idx = nearest(r, g, b)
                        nearestCache[key] = idx
                    }
                    indices[i] = idx.toByte()
                    diffuse(e, dir, (r - palR[idx]) * STRENGTH, (g - palG[idx]) * STRENGTH, (b - palB[idx]) * STRENGTH)
                }
                x += dir
            }
            val t = errCur; errCur = errNext; errNext = t
            errNext.fill(0f)
        }
    }

    /** Spreads the error to the next pixel (7/16) and to the row below (3/16, 5/16, 1/16), following [dir]. */
    private fun diffuse(e: Int, dir: Int, er: Float, eg: Float, eb: Float) {
        val ahead = e + dir * 3
        val behind = e - dir * 3
        for (c in 0..2) {
            val v = when (c) { 0 -> er; 1 -> eg; else -> eb }
            errCur[ahead + c] += v * 7f / 16f
            errNext[behind + c] += v * 3f / 16f
            errNext[e + c] += v * 5f / 16f
            errNext[ahead + c] += v * 1f / 16f
        }
    }

    private fun nearest(r: Float, g: Float, b: Float): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in 0 until paletteSize) {
            val dr = r - palR[i]; val dg = g - palG[i]; val db = b - palB[i]
            // Weighted distance, closer to how the eye perceives color differences
            val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun widestChannel(colors: IntArray, start: Int, end: Int): Int {
        var best = 0
        var bestRange = -1
        for (shift in intArrayOf(12, 6, 0)) {
            var lo = 63; var hi = 0
            for (j in start until end) {
                val v = (colors[j] shr shift) and 63
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            if (hi - lo > bestRange) { bestRange = hi - lo; best = shift }
        }
        return best
    }

    private fun sortRange(colors: IntArray, start: Int, end: Int, shift: Int) {
        // Sort on a packed (channel value, color) long to avoid boxing
        val packed = LongArray(end - start) { j ->
            val k = colors[start + j]
            (((k shr shift) and 63).toLong() shl 32) or k.toLong()
        }
        packed.sort()
        for (j in packed.indices) colors[start + j] = (packed[j] and 0xFFFFFFFFL).toInt()
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
        /** Share of the error that is diffused; slightly below 1 to limit grain and frame-to-frame shimmer. */
        private const val STRENGTH = 0.85f

        private fun key6(r: Int, g: Int, b: Int) = ((r shr 2) shl 12) or ((g shr 2) shl 6) or (b shr 2)

        /** Center of a 6-bit bin in the 8-bit range. */
        private fun center6(v: Int) = (v shl 2) + 1.5f
    }
}
