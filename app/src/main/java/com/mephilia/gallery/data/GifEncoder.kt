package com.mephilia.gallery.data

import java.io.Closeable
import java.io.OutputStream

/** Mask of the low byte of an int. */
private const val BYTE_MASK = 0xFF

/** Bits in a byte. */
private const val BITS_PER_BYTE = 8

/** Largest frame delay, in hundredths of a second (16-bit field). */
private const val MAX_DELAY_CS = 0xFFFF

/** Packed field of the header: no global color table, since each frame has its own. */
private const val NO_GLOBAL_COLOR_TABLE = 0x00

/** Background color index and pixel aspect ratio of the header: unused. */
private const val UNUSED = 0x00

/** Packed field of an image descriptor: a local table of 256 colors (2^(7 + 1)), not interlaced. */
private const val LOCAL_COLOR_TABLE_256 = GifFormat.COLOR_TABLE_FLAG or GifFormat.COLOR_TABLE_SIZE_MASK

/**
 * Animated GIF89a encoder with no Android dependency. Each frame gets its own 256-color palette (see
 * [GifQuantizer]), and frames are written as complete images.
 *
 * Call [addFrame] for each frame, then [close] to end the file.
 *
 * @param out where the GIF is written; it isn't closed by [close], only flushed.
 * @param loop makes the animation loop forever; otherwise it plays once.
 * @param ditherStrength share of the quantization error that is diffused (tuning, exposed for tests).
 * @param refinePasses number of k-means passes refining the median-cut palette (tuning, exposed for tests).
 * @param edgeAware dithers less on edges, where grain blurs details (tuning, exposed for tests).
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    loop: Boolean = true,
    internal val ditherStrength: Float = DEFAULT_DITHER_STRENGTH,
    internal val refinePasses: Int = DEFAULT_REFINE_PASSES,
    internal val edgeAware: Boolean = true,
) : Closeable {

    /** Builds each frame's palette. */
    private val quantizer = GifQuantizer(width, height, ditherStrength, refinePasses, edgeAware)

    init {
        require(width in 1..GifFormat.MAX_DIMENSION && height in 1..GifFormat.MAX_DIMENSION)
        out.write(GifFormat.SIGNATURE_89A.toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(NO_GLOBAL_COLOR_TABLE)
        out.write(UNUSED)
        out.write(UNUSED)
        if (loop) writeLoopForever()
    }

    /**
     * Adds a frame.
     *
     * @param argb width × height ARGB pixels, as from Bitmap.getPixels. Pixels with alpha below 128 are transparent.
     * @param delayCs how long the frame shows, in hundredths of a second.
     */
    fun addFrame(argb: IntArray, delayCs: Int) {
        require(argb.size == width * height)
        val colorTable = quantizer.quantize(argb)
        writeGraphicControl(delayCs, quantizer.transparentIndex)
        out.write(GifFormat.IMAGE_SEPARATOR)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out.write(LOCAL_COLOR_TABLE_256)
        out.write(colorTable)
        GifLzwEncoder(out).encode(quantizer.indices)
    }

    /** Ends the file. */
    override fun close() {
        out.write(GifFormat.TRAILER)
        out.flush()
    }

    /** Writes the NETSCAPE2.0 extension asking to loop forever; without it, the GIF plays once. */
    private fun writeLoopForever() {
        out.write(GifFormat.EXTENSION_INTRODUCER)
        out.write(GifFormat.APPLICATION_LABEL)
        out.write(GifFormat.APPLICATION_ID_SIZE)
        out.write(GifFormat.NETSCAPE_LOOP_ID.toByteArray(Charsets.US_ASCII))
        out.write(GifFormat.LOOP_SUB_BLOCK_SIZE)
        out.write(GifFormat.LOOP_SUB_BLOCK_ID)
        writeShort(GifFormat.LOOP_FOREVER)
        out.write(GifFormat.BLOCK_TERMINATOR)
    }

    /**
     * Writes the graphic control extension of the next frame: its [delayCs], and its [transparentIndex] if it has
     * one (-1 otherwise). Frames are complete images, so a frame with transparency must clear the previous one
     * (disposal "restore to background"), or the previous frame would show through.
     */
    private fun writeGraphicControl(delayCs: Int, transparentIndex: Int) {
        val hasTransparency = transparentIndex >= 0
        val packed = if (hasTransparency) {
            (GifFormat.DISPOSAL_BACKGROUND shl GifFormat.DISPOSAL_SHIFT) or GifFormat.TRANSPARENCY_FLAG
        } else {
            0
        }
        out.write(GifFormat.EXTENSION_INTRODUCER)
        out.write(GifFormat.GRAPHIC_CONTROL_LABEL)
        out.write(GifFormat.GRAPHIC_CONTROL_SIZE)
        out.write(packed)
        writeShort(delayCs.coerceIn(0, MAX_DELAY_CS))
        out.write(if (hasTransparency) transparentIndex else 0)
        out.write(GifFormat.BLOCK_TERMINATOR)
    }

    /** Writes a 16-bit value, little-endian. */
    private fun writeShort(value: Int) {
        out.write(value and BYTE_MASK)
        out.write((value shr BITS_PER_BYTE) and BYTE_MASK)
    }
}
