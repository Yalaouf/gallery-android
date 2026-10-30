package com.mephilia.gallery

import com.mephilia.gallery.data.GifEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

class GifEncoderTest {

    private val w = 160
    private val h = 120

    /** Gradient with a moving square; frame 2 is pure noise, to fill the LZW dictionary and force resets. */
    private fun frame(n: Int): IntArray {
        val rnd = Random(n)
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (n == 2) {
                0xFF000000.toInt() or rnd.nextInt(0x1000000)
            } else {
                val inSquare = x in (10 + n * 30) until (50 + n * 30) && y in 30 until 70
                val r = if (inSquare) 255 else x * 255 / w
                val g = if (inSquare) 40 else y * 255 / h
                val b = if (inSquare) 40 else 128
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun encode(frames: List<IntArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        GifEncoder(bytes, w, h).use { enc -> frames.forEach { enc.addFrame(it, 10) } }
        return bytes.toByteArray()
    }

    @Test
    fun writesValidGifStructure() {
        val gif = encode(List(4) { frame(it) })
        assertArrayEquals("GIF89a".toByteArray(), gif.copyOfRange(0, 6))
        assertEquals(w, (gif[6].toInt() and 0xFF) or ((gif[7].toInt() and 0xFF) shl 8))
        assertEquals(h, (gif[8].toInt() and 0xFF) or ((gif[9].toInt() and 0xFF) shl 8))
        assertEquals(0x3B, gif.last().toInt())
    }

    /** Writes the GIF and its source frames (PPM) to build/test-gifs, for checking with an external decoder. */
    @Test
    fun writesSamplesForExternalCheck() {
        val dir = File("build/test-gifs").apply { mkdirs() }
        val frames = List(4) { frame(it) }
        File(dir, "sample.gif").writeBytes(encode(frames))
        frames.forEachIndexed { n, px ->
            val ppm = ByteArrayOutputStream()
            ppm.write("P6\n$w $h\n255\n".toByteArray())
            for (p in px) {
                ppm.write((p shr 16) and 0xFF); ppm.write((p shr 8) and 0xFF); ppm.write(p and 0xFF)
            }
            File(dir, "frame$n.ppm").writeBytes(ppm.toByteArray())
        }
    }

    /** Encodes a natural-looking image (plasma gradients) to build/test-gifs/plasma.gif, for visual quality checks. */
    @Test
    fun writesPlasmaSample() {
        val img = javax.imageio.ImageIO.read(javaClass.classLoader!!.getResourceAsStream("gif/plasma.png"))
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        val bytes = ByteArrayOutputStream()
        GifEncoder(bytes, img.width, img.height).use { it.addFrame(px, 10) }
        File("build/test-gifs").apply { mkdirs() }.resolve("plasma.gif").writeBytes(bytes.toByteArray())
    }
}
