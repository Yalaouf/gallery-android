package com.mephilia.gallery.data

/** Most pixels accepted in a GIF; the size comes from the file, so absurd values are refused before allocating. */
private const val MAX_PIXELS = 50_000_000L

/** Delays shorter than this (in hundredths of a second) are shown as [DEFAULT_DELAY_CS], as browsers do. */
private const val MIN_DELAY_CS = 2

/** Delay used for frames with a zero or very short one, in hundredths of a second. */
private const val DEFAULT_DELAY_CS = 10

/** Color of indices missing from the color table. */
private const val OPAQUE_BLACK = 0xFF000000.toInt()

/** Fully transparent pixel. */
private const val TRANSPARENT = 0

/** Mask of the low byte of an int. */
private const val BYTE_MASK = 0xFF

/** Position of the red channel in an ARGB pixel. */
private const val RED_SHIFT = 16

/** Position of the green channel in an ARGB pixel. */
private const val GREEN_SHIFT = 8

/** No transparent color index. */
private const val NO_TRANSPARENCY = -1

/** First row of each pass of an interlaced image. */
private val INTERLACE_PASS_STARTS = intArrayOf(0, 4, 2, 1)

/** Row step of each pass of an interlaced image. */
private val INTERLACE_PASS_STEPS = intArrayOf(8, 8, 4, 2)

/**
 * GIF decoder (87a and 89a) with no Android dependency.
 *
 * Yields each frame fully composited, as a viewer would display it: partial frames, transparency,
 * disposal methods and interlacing are supported. The background color is ignored: the background
 * is transparent, as in browsers.
 *
 * @throws IllegalArgumentException if [data] isn't a GIF, or declares an empty or absurdly large size.
 */
class GifDecoder(data: ByteArray) {

    /**
     * A frame, as displayed.
     *
     * @param argb ARGB pixels of the whole image. Reused between frames: copy it to keep it.
     * @param delayCs display time in hundredths of a second.
     */
    class Frame(val argb: IntArray, val delayCs: Int)

    /** The settings a graphic control extension gives to the next frame. */
    private data class GraphicControl(
        val delayCs: Int = 0,
        val disposal: Int = GifFormat.DISPOSAL_NONE,
        val transparentIndex: Int = NO_TRANSPARENCY,
    )

    /** Position and size of a frame on the image. */
    private data class FrameRect(val left: Int, val top: Int, val width: Int, val height: Int)

    /** Reads the file. */
    private val reader = GifReader(data)

    /** Width of the image, in pixels. */
    var width = 0
        private set

    /** Height of the image, in pixels. */
    var height = 0
        private set

    /** Loop count from the NETSCAPE extension: null if the animation plays once, 0 if it loops forever. */
    var loopCount: Int? = null
        private set

    /** The color table shared by frames that have none of their own, if any. */
    private var globalPalette: IntArray? = null

    /** Position of the first block after the header. */
    private var headerEnd = 0

    init {
        val signature = reader.ascii(0, GifFormat.SIGNATURE_LENGTH)
        require(signature == GifFormat.SIGNATURE_87A || signature == GifFormat.SIGNATURE_89A) { "Not a GIF file" }
        reader.pos = GifFormat.SIGNATURE_LENGTH
        width = reader.u16()
        height = reader.u16()
        val packed = reader.u8()
        reader.u8()
        reader.u8()
        if (packed and GifFormat.COLOR_TABLE_FLAG != 0) globalPalette = readPalette(GifFormat.colorTableSize(packed))
        require(width > 0 && height > 0) { "Invalid GIF size" }
        require(width.toLong() * height <= MAX_PIXELS) { "GIF too large (${width}×$height)" }
        headerEnd = reader.pos
        loopCount = scanLoopCount()
    }

    /** Counts the frames without decoding them. */
    fun frameCount(): Int {
        var count = 0
        walk { kind -> if (kind == GifFormat.IMAGE_SEPARATOR) count++ }
        return count
    }

    /** Decodes the frames in order, calling [action] with each one composited over the previous ones. */
    fun forEachFrame(action: (Frame) -> Unit) {
        val canvas = IntArray(width * height)
        var saved: IntArray? = null
        var previousDisposal = GifFormat.DISPOSAL_NONE
        var previousRect = FrameRect(0, 0, 0, 0)
        var control = GraphicControl()

        reader.pos = headerEnd
        while (!reader.atEnd) {
            when (reader.u8()) {
                GifFormat.EXTENSION_INTRODUCER -> {
                    if (reader.u8() == GifFormat.GRAPHIC_CONTROL_LABEL) control = readGraphicControl()
                    reader.skipSubBlocks()
                }
                GifFormat.IMAGE_SEPARATOR -> {
                    when (previousDisposal) {
                        GifFormat.DISPOSAL_BACKGROUND -> clearRect(canvas, previousRect)
                        GifFormat.DISPOSAL_PREVIOUS -> saved?.copyInto(canvas)
                    }
                    val rect = FrameRect(reader.u16(), reader.u16(), reader.u16(), reader.u16())
                    val packed = reader.u8()
                    val palette = (if (packed and GifFormat.COLOR_TABLE_FLAG != 0) readPalette(GifFormat.colorTableSize(packed)) else globalPalette)
                        ?: IntArray(GifFormat.MAX_COLORS) { OPAQUE_BLACK }
                    val interlaced = packed and GifFormat.INTERLACE_FLAG != 0
                    if (control.disposal == GifFormat.DISPOSAL_PREVIOUS) saved = canvas.copyOf()

                    val indices = decodeGifLzw(reader, rect.width * rect.height)
                    draw(canvas, indices, palette, rect, interlaced, control.transparentIndex)
                    action(Frame(canvas, if (control.delayCs < MIN_DELAY_CS) DEFAULT_DELAY_CS else control.delayCs))

                    previousDisposal = control.disposal
                    previousRect = rect
                    control = GraphicControl()
                }
                else -> return
            }
        }
    }

    /** Reads a graphic control extension's data, leaving the reader at its terminator. */
    private fun readGraphicControl(): GraphicControl {
        val size = reader.u8()
        val start = reader.pos
        val packed = reader.u8()
        val delay = reader.u16()
        val transparentIndex = reader.u8()
        reader.pos = start + size
        return GraphicControl(
            delayCs = delay,
            disposal = (packed shr GifFormat.DISPOSAL_SHIFT) and GifFormat.DISPOSAL_MASK,
            transparentIndex = if (packed and GifFormat.TRANSPARENCY_FLAG != 0) transparentIndex else NO_TRANSPARENCY,
        )
    }

    /** The loop count from a NETSCAPE (or ANIMEXTS) application extension, or null if there is none. */
    private fun scanLoopCount(): Int? {
        var loop: Int? = null
        walk { kind ->
            if (kind != GifFormat.APPLICATION_LABEL) return@walk
            val idSize = reader.peek(reader.pos)
            val id = reader.ascii(reader.pos + 1, idSize)
            if (!id.startsWith(GifFormat.NETSCAPE_LOOP_ID) && !id.startsWith(GifFormat.ANIMEXTS_LOOP_ID)) return@walk
            val sub = reader.pos + 1 + idSize
            val isLoopBlock = reader.peek(sub) == GifFormat.LOOP_SUB_BLOCK_SIZE && reader.peek(sub + 1) == GifFormat.LOOP_SUB_BLOCK_ID
            if (sub + GifFormat.LOOP_SUB_BLOCK_SIZE < reader.size && isLoopBlock) {
                loop = reader.peek(sub + 2) or (reader.peek(sub + GifFormat.LOOP_SUB_BLOCK_SIZE) shl Byte.SIZE_BITS)
            }
        }
        return loop
    }

    /**
     * Walks the blocks without decoding them. [visit] gets [GifFormat.IMAGE_SEPARATOR] for an image, or the
     * extension's label for an extension, with the reader at the block's data.
     */
    private fun walk(visit: (Int) -> Unit) {
        reader.pos = headerEnd
        while (!reader.atEnd) {
            when (reader.u8()) {
                GifFormat.EXTENSION_INTRODUCER -> {
                    val label = reader.u8()
                    val mark = reader.pos
                    visit(label)
                    reader.pos = mark
                    reader.skipSubBlocks()
                }
                GifFormat.IMAGE_SEPARATOR -> {
                    visit(GifFormat.IMAGE_SEPARATOR)
                    reader.pos += GifFormat.IMAGE_DESCRIPTOR_POSITION_SIZE
                    val packed = reader.u8()
                    if (packed and GifFormat.COLOR_TABLE_FLAG != 0) {
                        reader.pos += GifFormat.BYTES_PER_COLOR * GifFormat.colorTableSize(packed)
                    }
                    reader.u8()
                    reader.skipSubBlocks()
                }
                else -> return
            }
        }
    }

    /** Draws a frame's [indices] at [rect] on [canvas], skipping the transparent index and what falls outside. */
    private fun draw(canvas: IntArray, indices: ByteArray, palette: IntArray, rect: FrameRect, interlaced: Boolean, transparent: Int) {
        for (row in 0 until rect.height) {
            val y = rect.top + (if (interlaced) interlacedRow(row, rect.height) else row)
            if (y !in 0 until height) continue
            val source = row * rect.width
            val destination = y * width
            for (column in 0 until rect.width) {
                val x = rect.left + column
                if (x !in 0 until width) continue
                val index = indices[source + column].toInt() and BYTE_MASK
                if (index == transparent) continue
                canvas[destination + x] = palette.getOrElse(index) { OPAQUE_BLACK }
            }
        }
    }

    /**
     * The actual row of the [n]-th row stored in an interlaced image of [height] rows. Rows are stored in four
     * passes: every 8th row from 0, every 8th from 4, every 4th from 2, then every 2nd from 1.
     */
    private fun interlacedRow(n: Int, height: Int): Int {
        var remaining = n
        for (pass in INTERLACE_PASS_STARTS.indices) {
            val start = INTERLACE_PASS_STARTS[pass]
            val step = INTERLACE_PASS_STEPS[pass]
            val rowsInPass = (height - start + step - 1) / step
            if (remaining < rowsInPass) return start + remaining * step
            remaining -= rowsInPass
        }
        return n
    }

    /** Clears [rect] on [canvas] to transparent, as the "restore to background" disposal asks. */
    private fun clearRect(canvas: IntArray, rect: FrameRect) {
        for (y in maxOf(0, rect.top) until minOf(height, rect.top + rect.height)) {
            for (x in maxOf(0, rect.left) until minOf(width, rect.left + rect.width)) canvas[y * width + x] = TRANSPARENT
        }
    }

    /** Reads a color table of [size] colors as opaque ARGB values. */
    private fun readPalette(size: Int): IntArray = IntArray(size) {
        val red = reader.u8()
        val green = reader.u8()
        val blue = reader.u8()
        OPAQUE_BLACK or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }
}
