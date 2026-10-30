package com.mephilia.gallery.data

/**
 * GIF decoder (87a/89a) with no Android dependency.
 *
 * Yields each frame fully composited, as a viewer would display it: partial frames,
 * transparency, disposal methods and interlacing are supported.
 */
class GifDecoder(private val data: ByteArray) {

    class Frame(
        /** ARGB pixels of the whole logical screen. Reused between frames: copy it to keep it. */
        val argb: IntArray,
        /** Display time in hundredths of a second. */
        val delayCs: Int,
    )

    var width = 0
        private set
    var height = 0
        private set

    /** Loop count from the NETSCAPE extension: null = played once, 0 = loops forever. */
    var loopCount: Int? = null
        private set

    private var pos = 0
    private var globalPalette: IntArray? = null
    private var headerEnd = 0

    init {
        val sig = String(data, 0, minOf(6, data.size), Charsets.US_ASCII)
        require(sig == "GIF87a" || sig == "GIF89a") { "Not a GIF file" }
        pos = 6
        width = u16()
        height = u16()
        val packed = u8()
        u8() // background color index: ignored, the background is transparent as in browsers
        u8()
        if (packed and 0x80 != 0) globalPalette = readPalette(2 shl (packed and 7))
        require(width > 0 && height > 0) { "Invalid GIF size" }
        loopCount = scanLoopCount()
    }

    /** Counts frames without decoding them. */
    fun frameCount(): Int {
        var count = 0
        walk { kind -> if (kind == 0x2C) count++ }
        return count
    }

    fun forEachFrame(action: (Frame) -> Unit) {
        val canvas = IntArray(width * height)
        var saved: IntArray? = null
        var prevDisposal = 0
        var prevRect = IntArray(4)

        var delay = 0
        var disposal = 0
        var transparent = -1

        pos = headerEnd
        while (pos < data.size) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    if (label == 0xF9) {
                        val size = u8()
                        val start = pos
                        val packed = u8()
                        delay = u16()
                        val ti = u8()
                        disposal = (packed shr 2) and 7
                        transparent = if (packed and 1 != 0) ti else -1
                        pos = start + size
                        skipSubBlocks()
                    } else {
                        skipSubBlocks()
                    }
                }
                0x2C -> {
                    when (prevDisposal) {
                        2 -> clearRect(canvas, prevRect)
                        3 -> saved?.copyInto(canvas)
                    }
                    val left = u16()
                    val top = u16()
                    val w = u16()
                    val h = u16()
                    val packed = u8()
                    val palette = if (packed and 0x80 != 0) readPalette(2 shl (packed and 7)) else globalPalette
                        ?: IntArray(256) { 0xFF000000.toInt() }
                    val interlaced = packed and 0x40 != 0
                    if (disposal == 3) saved = canvas.copyOf()

                    val indices = decodeLzw(w * h)
                    draw(canvas, indices, palette, left, top, w, h, interlaced, transparent)

                    // Browsers show zero or very short delays as 10 cs
                    action(Frame(canvas, if (delay < 2) 10 else delay))

                    prevDisposal = disposal
                    prevRect = intArrayOf(left, top, w, h)
                    delay = 0
                    disposal = 0
                    transparent = -1
                }
                0x3B -> return
                else -> return
            }
        }
    }

    private fun scanLoopCount(): Int? {
        headerEnd = pos
        var loop: Int? = null
        walk { kind ->
            if (kind == 0xFF) {
                val size = data[pos].toInt() and 0xFF
                val id = String(data, pos + 1, minOf(size, data.size - pos - 1), Charsets.US_ASCII)
                if (id.startsWith("NETSCAPE2.0") || id.startsWith("ANIMEXTS1.0")) {
                    val sub = pos + 1 + size
                    if (sub + 3 < data.size && data[sub].toInt() == 3 && data[sub + 1].toInt() == 1) {
                        loop = (data[sub + 2].toInt() and 0xFF) or ((data[sub + 3].toInt() and 0xFF) shl 8)
                    }
                }
            }
        }
        return loop
    }

    /** Walks the blocks without decoding; [visit] gets 0x2C for an image, or the extension label. */
    private fun walk(visit: (Int) -> Unit) {
        pos = headerEnd
        while (pos < data.size) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    val mark = pos
                    visit(label)
                    pos = mark
                    skipSubBlocks()
                }
                0x2C -> {
                    visit(0x2C)
                    pos += 8
                    val packed = u8()
                    if (packed and 0x80 != 0) pos += 3 * (2 shl (packed and 7))
                    pos++ // LZW minimum code size
                    skipSubBlocks()
                }
                else -> return
            }
        }
    }

    private fun draw(
        canvas: IntArray, indices: ByteArray, palette: IntArray,
        left: Int, top: Int, w: Int, h: Int, interlaced: Boolean, transparent: Int,
    ) {
        for (row in 0 until h) {
            val y = top + (if (interlaced) interlacedRow(row, h) else row)
            if (y !in 0 until height) continue
            val src = row * w
            val dst = y * width
            for (col in 0 until w) {
                val x = left + col
                if (x !in 0 until width) continue
                val idx = indices[src + col].toInt() and 0xFF
                if (idx == transparent) continue
                canvas[dst + x] = palette.getOrElse(idx) { 0xFF000000.toInt() }
            }
        }
    }

    /** Actual row of the n-th transmitted row of an interlaced image (passes: 0+8n, 4+8n, 2+4n, 1+2n). */
    private fun interlacedRow(n: Int, h: Int): Int {
        val p1 = (h + 7) / 8
        val p2 = (h + 3) / 8
        val p3 = (h + 1) / 4
        return when {
            n < p1 -> n * 8
            n < p1 + p2 -> (n - p1) * 8 + 4
            n < p1 + p2 + p3 -> (n - p1 - p2) * 4 + 2
            else -> (n - p1 - p2 - p3) * 2 + 1
        }
    }

    private fun clearRect(canvas: IntArray, r: IntArray) {
        val (left, top, w, h) = r.toList()
        for (y in maxOf(0, top) until minOf(height, top + h)) {
            for (x in maxOf(0, left) until minOf(width, left + w)) canvas[y * width + x] = 0
        }
    }

    private fun decodeLzw(count: Int): ByteArray {
        val minCodeSize = u8()
        val out = ByteArray(count)
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        val prefix = ShortArray(4096)
        val suffix = ByteArray(4096)
        val stack = ByteArray(4097)
        for (i in 0 until clear) suffix[i] = i.toByte()

        var codeSize = minCodeSize + 1
        var next = clear + 2
        var old = -1
        var first = 0
        var outPos = 0
        var bits = 0
        var bitCount = 0
        var blockLeft = 0

        loop@ while (outPos < count) {
            while (bitCount < codeSize) {
                if (blockLeft == 0) {
                    if (pos >= data.size) break@loop
                    blockLeft = u8()
                    if (blockLeft == 0) break@loop
                }
                if (pos >= data.size) break@loop
                bits = bits or (u8() shl bitCount)
                bitCount += 8
                blockLeft--
            }
            val code = bits and ((1 shl codeSize) - 1)
            bits = bits ushr codeSize
            bitCount -= codeSize

            if (code == clear) {
                codeSize = minCodeSize + 1
                next = clear + 2
                old = -1
                continue
            }
            if (code == eoi) break

            var sp = 0
            var cur = code
            if (old == -1) {
                if (code >= clear) break
                out[outPos++] = suffix[code]
                old = code
                first = code
                continue
            }
            if (cur >= next) {
                // KwKwK case: the code is not in the table yet
                stack[sp++] = first.toByte()
                cur = old
            }
            while (cur >= clear) {
                stack[sp++] = suffix[cur]
                cur = prefix[cur].toInt()
            }
            first = suffix[cur].toInt() and 0xFF
            stack[sp++] = first.toByte()
            while (sp > 0 && outPos < count) out[outPos++] = stack[--sp]

            if (next < 4096) {
                prefix[next] = old.toShort()
                suffix[next] = first.toByte()
                next++
                if (next == (1 shl codeSize) && codeSize < 12) codeSize++
            }
            old = code
        }
        while (blockLeft > 0 && pos < data.size) { pos++; blockLeft-- }
        skipSubBlocks()
        return out
    }

    private fun readPalette(n: Int): IntArray = IntArray(n) {
        val r = u8(); val g = u8(); val b = u8()
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun skipSubBlocks() {
        while (pos < data.size) {
            val size = u8()
            if (size == 0) return
            pos += size
        }
    }

    private fun u8(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else 0
    private fun u16(): Int = u8() or (u8() shl 8)
}
