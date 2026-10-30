package com.mephilia.gallery

import com.mephilia.gallery.data.GifDecoder
import com.mephilia.gallery.data.GifEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class GifDecoderTest {

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("gif/$name")!!.readBytes()

    private fun readPam(name: String): Triple<Int, Int, IntArray> {
        val bytes = resource(name)
        val headerEnd = String(bytes, Charsets.ISO_8859_1).indexOf("ENDHDR\n") + "ENDHDR\n".length
        val header = String(bytes, 0, headerEnd, Charsets.US_ASCII).lines()
            .mapNotNull { l -> l.split(" ").takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val w = header.getValue("WIDTH").toInt()
        val h = header.getValue("HEIGHT").toInt()
        val depth = header.getValue("DEPTH").toInt()
        val px = IntArray(w * h) { i ->
            val o = headerEnd + i * depth
            val r = bytes[o].toInt() and 0xFF
            val g = bytes[o + 1].toInt() and 0xFF
            val b = bytes[o + 2].toInt() and 0xFF
            val a = if (depth == 4) bytes[o + 3].toInt() and 0xFF else 255
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Triple(w, h, px)
    }

    /** Compares each decoded frame with ImageMagick's coalesced frames (generated with `magick X.gif -coalesce X-%d.pam`). */
    private fun checkAgainstImageMagick(gif: String, expectedFrames: Int, expectedDelayCs: Int) {
        val decoder = GifDecoder(resource("$gif.gif"))
        assertEquals(expectedFrames, decoder.frameCount())
        assertEquals(0, decoder.loopCount)
        var n = 0
        decoder.forEachFrame { frame ->
            val (w, h, expected) = readPam("$gif-$n.pam")
            assertEquals(w, decoder.width)
            assertEquals(h, decoder.height)
            assertEquals(expectedDelayCs, frame.delayCs)
            for (i in expected.indices) {
                val e = expected[i]
                val got = frame.argb[i]
                if ((e ushr 24) == 0) {
                    assertEquals("$gif frame $n pixel $i should be transparent", 0, got ushr 24)
                } else {
                    assertEquals("$gif frame $n pixel $i", e or (0xFF shl 24), got)
                }
            }
            n++
        }
        assertEquals(expectedFrames, n)
    }

    @Test fun optimizedPartialFrames() = checkAgainstImageMagick("opt", 3, 10)
    @Test fun disposeBackground() = checkAgainstImageMagick("bg", 3, 7)
    @Test fun disposePrevious() = checkAgainstImageMagick("prev", 3, 15)
    @Test fun interlaced() = checkAgainstImageMagick("inter", 2, 20)

    @Test
    fun roundTripWithTransparencyAndNoLoop() {
        val w = 50
        val h = 40
        val frames = List(3) { n ->
            IntArray(w * h) { i ->
                val x = i % w
                val y = i / w
                if (x < 10 + n * 10) 0 // transparent band that widens
                else (0xFF shl 24) or ((x * 5) shl 16) or ((y * 6) shl 8) or (n * 80)
            }
        }
        val bytes = ByteArrayOutputStream()
        GifEncoder(bytes, w, h, loop = false).use { enc -> frames.forEach { enc.addFrame(it, 12) } }

        val decoder = GifDecoder(bytes.toByteArray())
        assertNull("no NETSCAPE extension when loop is off", decoder.loopCount)
        var n = 0
        decoder.forEachFrame { frame ->
            assertEquals(12, frame.delayCs)
            val src = frames[n]
            var totalErr = 0L
            for (i in src.indices) {
                val s = src[i]
                val d = frame.argb[i]
                if ((s ushr 24) == 0) {
                    assertEquals("frame $n pixel $i should be transparent", 0, d ushr 24)
                } else {
                    assertEquals(0xFF, d ushr 24)
                    totalErr += abs(((s shr 16) and 0xFF) - ((d shr 16) and 0xFF)) +
                        abs(((s shr 8) and 0xFF) - ((d shr 8) and 0xFF)) + abs((s and 0xFF) - (d and 0xFF))
                }
            }
            assertTrue("frame $n average error too high: ${totalErr / (src.size * 3.0)}", totalErr / (src.size * 3.0) < 8)
            n++
        }
        assertEquals(3, n)
    }

    @Test
    fun fullyTransparentFrame() {
        val bytes = ByteArrayOutputStream()
        GifEncoder(bytes, 8, 8).use { it.addFrame(IntArray(64), 10) }
        GifDecoder(bytes.toByteArray()).forEachFrame { f -> assertTrue(f.argb.all { (it ushr 24) == 0 }) }
    }
}
