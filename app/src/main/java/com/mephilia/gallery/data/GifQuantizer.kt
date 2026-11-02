package com.mephilia.gallery.data

import kotlin.math.abs
import kotlin.math.roundToInt

/** Bits kept per channel in the color histogram. */
private const val HISTOGRAM_BITS = 6

/** Bits dropped from an 8-bit channel to get its histogram bin. */
private const val BIN_SHIFT = 8 - HISTOGRAM_BITS

/** Mask of one channel in a histogram key. */
private const val BIN_MASK = (1 shl HISTOGRAM_BITS) - 1

/** Size of the histogram: one entry per 18-bit color (6 bits per channel). */
private const val HISTOGRAM_SIZE = 1 shl (3 * HISTOGRAM_BITS)

/** Position of the red bin in a histogram key. */
private const val KEY_RED_SHIFT = 2 * HISTOGRAM_BITS

/** Position of the green bin in a histogram key. */
private const val KEY_GREEN_SHIFT = HISTOGRAM_BITS

/** The channel shifts of a histogram key, red, green and blue, for picking the channel to split on. */
private val KEY_CHANNEL_SHIFTS = intArrayOf(KEY_RED_SHIFT, KEY_GREEN_SHIFT, 0)

/** Offset from the bottom of a bin to its center, in 8-bit units. */
private const val BIN_CENTER_OFFSET = 1.5f

/** Position of the alpha channel in an ARGB pixel. */
private const val ALPHA_SHIFT = 24

/** Position of the red channel in an ARGB pixel. */
private const val RED_SHIFT = 16

/** Position of the green channel in an ARGB pixel. */
private const val GREEN_SHIFT = 8

/** Mask of one 8-bit channel. */
private const val CHANNEL_MASK = 0xFF

/** Largest value of an 8-bit channel. */
private const val CHANNEL_MAX = 255f

/** Pixels with less alpha than this are transparent; the others are opaque. */
private const val OPAQUE_ALPHA = 128

/** Channels per color. */
private const val CHANNELS = 3

/** Floyd–Steinberg weights are in sixteenths. */
private const val FLOYD_STEINBERG_SCALE = 16f

/** Share of the error passed to the next pixel of the row. */
private const val WEIGHT_AHEAD = 7f / FLOYD_STEINBERG_SCALE

/** Share of the error passed to the pixel below and behind. */
private const val WEIGHT_BELOW_BEHIND = 3f / FLOYD_STEINBERG_SCALE

/** Share of the error passed to the pixel right below. */
private const val WEIGHT_BELOW = 5f / FLOYD_STEINBERG_SCALE

/** Share of the error passed to the pixel below and ahead. */
private const val WEIGHT_BELOW_AHEAD = 1f / FLOYD_STEINBERG_SCALE

/** Weight of red in the color distance; the weights follow how the eye perceives differences. */
private const val DISTANCE_WEIGHT_RED = 2

/** Weight of green in the color distance: the eye is most sensitive to it. */
private const val DISTANCE_WEIGHT_GREEN = 4

/** Weight of blue in the color distance. */
private const val DISTANCE_WEIGHT_BLUE = 3

/** Weight of red in the luma (ITU-R BT.601), in thousandths. */
private const val LUMA_RED = 299

/** Weight of green in the luma, in thousandths. */
private const val LUMA_GREEN = 587

/** Weight of blue in the luma, in thousandths. */
private const val LUMA_BLUE = 114

/** The luma weights are in thousandths. */
private const val LUMA_SCALE = 1000

/** Neighbour luma difference (0 to 255) above which a pixel counts as a full edge. */
private const val EDGE_CONTRAST = 48f

/** Share of the error still diffused on a full edge. */
private const val MIN_EDGE_FACTOR = 0.2f

/** Marks a color whose nearest palette entry isn't known yet. */
private const val UNKNOWN = -1

/** Share of the error that is diffused by default; slightly below 1 to limit grain and frame-to-frame shimmer. */
internal const val DEFAULT_DITHER_STRENGTH = 0.85f

/** Number of k-means passes refining the median-cut palette by default. */
internal const val DEFAULT_REFINE_PASSES = 3

/**
 * Reduces frames of [width] × [height] ARGB pixels to a palette of at most 256 colors, for [GifEncoder].
 *
 * Each frame gets its own palette: median cut over an 18-bit histogram (6 bits per channel), refined by
 * k-means passes. Pixels are then mapped with serpentine Floyd–Steinberg error diffusion, which hides banding
 * in gradients far better than ordered dithering. Pixels with alpha below 128 become transparent.
 *
 * @param ditherStrength share of the quantization error that is diffused.
 * @param refinePasses number of k-means passes refining the palette.
 * @param edgeAware dithers less on sharp edges (text, outlines), where grain only blurs details.
 */
internal class GifQuantizer(
    private val width: Int,
    private val height: Int,
    private val ditherStrength: Float,
    private val refinePasses: Int,
    private val edgeAware: Boolean,
) {
    /** How many pixels of the frame have each 18-bit color. */
    private val histogram = IntArray(HISTOGRAM_SIZE)

    /** Nearest palette entry for each 18-bit color, filled lazily during dithering. */
    private val nearestCache = IntArray(HISTOGRAM_SIZE)

    /** Palette index of each pixel of the last quantized frame. */
    val indices = ByteArray(width * height)

    /** Red of each palette color. */
    private val paletteRed = FloatArray(GifFormat.MAX_COLORS)

    /** Green of each palette color. */
    private val paletteGreen = FloatArray(GifFormat.MAX_COLORS)

    /** Blue of each palette color. */
    private val paletteBlue = FloatArray(GifFormat.MAX_COLORS)

    /** Number of colors in the palette. */
    private var paletteSize = 0

    /** Index of the transparent color of the last quantized frame, or -1 if it has none. */
    var transparentIndex = UNKNOWN
        private set

    /** Diffused error for the current row, three channels per pixel, with a one-pixel border on each side. */
    private var errorCurrent = FloatArray((width + 2) * CHANNELS)

    /** Diffused error for the next row, laid out like [errorCurrent]. */
    private var errorNext = FloatArray((width + 2) * CHANNELS)

    /**
     * Builds the palette of a frame of [argb] pixels and fills [indices]. Returns the color table to write:
     * 256 RGB triplets, unused entries left black.
     */
    fun quantize(argb: IntArray): ByteArray {
        histogram.fill(0)
        var hasTransparency = false
        for (pixel in argb) {
            if (isTransparent(pixel)) {
                hasTransparency = true
                continue
            }
            histogram[histogramKey(red(pixel), green(pixel), blue(pixel))]++
        }
        val colors = usedColors()

        buildPalette(colors, if (hasTransparency) GifFormat.MAX_COLORS - 1 else GifFormat.MAX_COLORS)
        repeat(refinePasses) { refinePalette(colors) }
        transparentIndex = if (hasTransparency) paletteSize else UNKNOWN

        val table = ByteArray(GifFormat.MAX_COLORS * GifFormat.BYTES_PER_COLOR)
        for (i in 0 until paletteSize) {
            val offset = i * GifFormat.BYTES_PER_COLOR
            table[offset] = toChannelByte(paletteRed[i])
            table[offset + 1] = toChannelByte(paletteGreen[i])
            table[offset + 2] = toChannelByte(paletteBlue[i])
            paletteRed[i] = (table[offset].toInt() and CHANNEL_MASK).toFloat()
            paletteGreen[i] = (table[offset + 1].toInt() and CHANNEL_MASK).toFloat()
            paletteBlue[i] = (table[offset + 2].toInt() and CHANNEL_MASK).toFloat()
        }
        dither(argb)
        return table
    }

    /**
     * Median cut: splits the most populated box that still holds several colors, at its median along its widest
     * channel, until there are [maxColors] boxes. Each palette color is then the weighted mean of its box.
     */
    private fun buildPalette(colors: IntArray, maxColors: Int) {
        val boxes = ArrayList<IntRange>()
        if (colors.isNotEmpty()) boxes += 0 until colors.size
        while (boxes.size < maxColors) {
            val best = mostPopulatedSplittableBox(colors, boxes)
            if (best < 0) break
            val box = boxes[best]
            val total = population(colors, box)
            sortRange(colors, box, widestChannel(colors, box))
            var accumulated = 0L
            var cut = box.first + 1
            for (j in box) {
                accumulated += histogram[colors[j]]
                if (accumulated * 2 >= total) {
                    cut = (j + 1).coerceIn(box.first + 1, box.last)
                    break
                }
            }
            boxes[best] = box.first until cut
            boxes += cut..box.last
        }
        paletteSize = boxes.size
        for ((i, box) in boxes.withIndex()) {
            var sumRed = 0.0
            var sumGreen = 0.0
            var sumBlue = 0.0
            var count = 0.0
            for (j in box) {
                val key = colors[j]
                val weight = histogram[key].toDouble()
                sumRed += binCenter(key shr KEY_RED_SHIFT) * weight
                sumGreen += binCenter(key shr KEY_GREEN_SHIFT) * weight
                sumBlue += binCenter(key) * weight
                count += weight
            }
            paletteRed[i] = (sumRed / count).toFloat()
            paletteGreen[i] = (sumGreen / count).toFloat()
            paletteBlue[i] = (sumBlue / count).toFloat()
        }
    }

    /** The histogram keys of the colors present in the frame, in increasing order. */
    private fun usedColors(): IntArray {
        var count = 0
        for (pixels in histogram) if (pixels > 0) count++
        val colors = IntArray(count)
        count = 0
        for (key in histogram.indices) if (histogram[key] > 0) colors[count++] = key
        return colors
    }

    /** Index of the box with the most pixels among those holding at least two colors, or -1 if there is none. */
    private fun mostPopulatedSplittableBox(colors: IntArray, boxes: List<IntRange>): Int {
        var best = -1
        var bestPopulation = -1L
        for ((i, box) in boxes.withIndex()) {
            if (box.last - box.first < 1) continue
            val population = population(colors, box)
            if (population > bestPopulation) {
                bestPopulation = population
                best = i
            }
        }
        return best
    }

    /** Number of pixels whose color falls in [box]. */
    private fun population(colors: IntArray, box: IntRange): Long {
        var total = 0L
        for (j in box) total += histogram[colors[j]]
        return total
    }

    /** One k-means pass: moves each palette color to the weighted mean of the colors nearest to it. */
    private fun refinePalette(colors: IntArray) {
        if (paletteSize < 2) return
        val sumRed = DoubleArray(paletteSize)
        val sumGreen = DoubleArray(paletteSize)
        val sumBlue = DoubleArray(paletteSize)
        val count = DoubleArray(paletteSize)
        for (key in colors) {
            val r = binCenter(key shr KEY_RED_SHIFT)
            val g = binCenter(key shr KEY_GREEN_SHIFT)
            val b = binCenter(key)
            val i = nearest(r, g, b)
            val weight = histogram[key].toDouble()
            sumRed[i] += r * weight
            sumGreen[i] += g * weight
            sumBlue[i] += b * weight
            count[i] += weight
        }
        for (i in 0 until paletteSize) {
            if (count[i] > 0) {
                paletteRed[i] = (sumRed[i] / count[i]).toFloat()
                paletteGreen[i] = (sumGreen[i] / count[i]).toFloat()
                paletteBlue[i] = (sumBlue[i] / count[i]).toFloat()
            }
        }
    }

    /**
     * Maps [argb] to palette [indices] with serpentine Floyd–Steinberg error diffusion: even rows go left to right,
     * odd rows right to left, which avoids the diagonal drift of one-way diffusion.
     */
    private fun dither(argb: IntArray) {
        nearestCache.fill(UNKNOWN)
        errorCurrent.fill(0f)
        errorNext.fill(0f)
        for (y in 0 until height) {
            val leftToRight = y % 2 == 0
            val direction = if (leftToRight) 1 else -1
            var x = if (leftToRight) 0 else width - 1
            repeat(width) {
                val i = y * width + x
                val pixel = argb[i]
                if (isTransparent(pixel)) {
                    indices[i] = transparentIndex.toByte()
                } else {
                    val e = (x + 1) * CHANNELS
                    val r = (red(pixel) + errorCurrent[e]).coerceIn(0f, CHANNEL_MAX)
                    val g = (green(pixel) + errorCurrent[e + 1]).coerceIn(0f, CHANNEL_MAX)
                    val b = (blue(pixel) + errorCurrent[e + 2]).coerceIn(0f, CHANNEL_MAX)
                    val key = histogramKey(r.toInt(), g.toInt(), b.toInt())
                    var index = nearestCache[key]
                    if (index == UNKNOWN) {
                        index = nearest(r, g, b)
                        nearestCache[key] = index
                    }
                    indices[i] = index.toByte()
                    val strength = if (edgeAware) ditherStrength * edgeFactor(argb, x, y) else ditherStrength
                    diffuse(e, direction, (r - paletteRed[index]) * strength, (g - paletteGreen[index]) * strength, (b - paletteBlue[index]) * strength)
                }
                x += direction
            }
            val done = errorCurrent
            errorCurrent = errorNext
            errorNext = done
            errorNext.fill(0f)
        }
    }

    /**
     * How much of the error to diffuse at a pixel: all of it in smooth areas (where dithering hides banding),
     * down to [MIN_EDGE_FACTOR] on sharp edges such as text and outlines, where dithering only adds grain.
     */
    private fun edgeFactor(argb: IntArray, x: Int, y: Int): Float {
        val i = y * width + x
        val center = luma(argb[i])
        var contrast = 0
        if (x + 1 < width) contrast = maxOf(contrast, abs(center - luma(argb[i + 1])))
        if (x > 0) contrast = maxOf(contrast, abs(center - luma(argb[i - 1])))
        if (y + 1 < height) contrast = maxOf(contrast, abs(center - luma(argb[i + width])))
        if (y > 0) contrast = maxOf(contrast, abs(center - luma(argb[i - width])))
        return (1f - contrast / EDGE_CONTRAST).coerceIn(MIN_EDGE_FACTOR, 1f)
    }

    /** Spreads a pixel's error ([errorRed], [errorGreen], [errorBlue]) to its neighbours ahead and below, following [direction]. */
    private fun diffuse(e: Int, direction: Int, errorRed: Float, errorGreen: Float, errorBlue: Float) {
        val ahead = e + direction * CHANNELS
        val behind = e - direction * CHANNELS
        for (channel in 0 until CHANNELS) {
            val error = when (channel) {
                0 -> errorRed
                1 -> errorGreen
                else -> errorBlue
            }
            errorCurrent[ahead + channel] += error * WEIGHT_AHEAD
            errorNext[behind + channel] += error * WEIGHT_BELOW_BEHIND
            errorNext[e + channel] += error * WEIGHT_BELOW
            errorNext[ahead + channel] += error * WEIGHT_BELOW_AHEAD
        }
    }

    /** Index of the palette color nearest to ([r], [g], [b]), with a perceptually weighted distance. */
    private fun nearest(r: Float, g: Float, b: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until paletteSize) {
            val dr = r - paletteRed[i]
            val dg = g - paletteGreen[i]
            val db = b - paletteBlue[i]
            val distance = DISTANCE_WEIGHT_RED * dr * dr + DISTANCE_WEIGHT_GREEN * dg * dg + DISTANCE_WEIGHT_BLUE * db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    /** The shift (in a histogram key) of the channel with the widest range of values in [box]. */
    private fun widestChannel(colors: IntArray, box: IntRange): Int {
        var best = 0
        var bestRange = -1
        for (shift in KEY_CHANNEL_SHIFTS) {
            var low = BIN_MASK
            var high = 0
            for (j in box) {
                val value = (colors[j] shr shift) and BIN_MASK
                if (value < low) low = value
                if (value > high) high = value
            }
            if (high - low > bestRange) {
                bestRange = high - low
                best = shift
            }
        }
        return best
    }

    /** Sorts [box] of [colors] by the channel at [shift], packing (value, color) in a long to avoid boxing. */
    private fun sortRange(colors: IntArray, box: IntRange, shift: Int) {
        val packed = LongArray(box.last - box.first + 1) { j ->
            val key = colors[box.first + j]
            (((key shr shift) and BIN_MASK).toLong() shl Int.SIZE_BITS) or key.toLong()
        }
        packed.sort()
        for (j in packed.indices) colors[box.first + j] = packed[j].toInt()
    }

    private companion object {
        /** Whether [pixel] counts as transparent. */
        fun isTransparent(pixel: Int) = (pixel ushr ALPHA_SHIFT) < OPAQUE_ALPHA

        /** Red channel of an ARGB pixel. */
        fun red(pixel: Int) = (pixel shr RED_SHIFT) and CHANNEL_MASK

        /** Green channel of an ARGB pixel. */
        fun green(pixel: Int) = (pixel shr GREEN_SHIFT) and CHANNEL_MASK

        /** Blue channel of an ARGB pixel. */
        fun blue(pixel: Int) = pixel and CHANNEL_MASK

        /** Perceived brightness of an ARGB pixel, from 0 to 255. */
        fun luma(pixel: Int) = (red(pixel) * LUMA_RED + green(pixel) * LUMA_GREEN + blue(pixel) * LUMA_BLUE) / LUMA_SCALE

        /** Histogram key of an 8-bit color: 6 bits per channel. */
        fun histogramKey(r: Int, g: Int, b: Int) =
            ((r shr BIN_SHIFT) shl KEY_RED_SHIFT) or ((g shr BIN_SHIFT) shl KEY_GREEN_SHIFT) or (b shr BIN_SHIFT)

        /** Center of a 6-bit bin (the low bits of [bin]) in the 8-bit range. */
        fun binCenter(bin: Int) = ((bin and BIN_MASK) shl BIN_SHIFT) + BIN_CENTER_OFFSET

        /** A palette value rounded to a channel byte. */
        fun toChannelByte(value: Float) = value.roundToInt().coerceIn(0, CHANNEL_MAX.toInt()).toByte()
    }
}
