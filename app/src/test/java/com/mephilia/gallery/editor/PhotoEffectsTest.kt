package com.mephilia.gallery.editor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.mephilia.gallery.ui.editor.Adjustments
import com.mephilia.gallery.ui.editor.Mark
import com.mephilia.gallery.ui.editor.mosaic
import com.mephilia.gallery.ui.editor.withEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEffectsTest {
    private fun solid(color: Int, w: Int = 20, h: Int = 20) =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    /** Random pixels: any blur or block shows as lost detail. */
    private fun noise(w: Int = 200, h: Int = 200) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        val rnd = Random(1)
        for (y in 0 until h) for (x in 0 until w) setPixel(x, y, Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)))
    }

    private val gray = Color.rgb(100, 100, 100)

    @Test
    fun noEffectReturnsTheSameImage() {
        val b = solid(gray)
        assertSame(b, b.withEffects(Adjustments(), emptyList()))
    }

    @Test
    fun lightMakesBrighterOrDarker() {
        val brighter = solid(gray).withEffects(Adjustments(brightness = 0.5f), emptyList()).getPixel(5, 5)
        val darker = solid(gray).withEffects(Adjustments(brightness = -0.5f), emptyList()).getPixel(5, 5)
        assertTrue(Color.red(brighter) > 100)
        assertTrue(Color.red(darker) < 100)
    }

    @Test
    fun contrastPushesAwayFromMidGray() {
        val light = solid(Color.rgb(180, 180, 180)).withEffects(Adjustments(contrast = 1f), emptyList()).getPixel(0, 0)
        val dark = solid(Color.rgb(70, 70, 70)).withEffects(Adjustments(contrast = 1f), emptyList()).getPixel(0, 0)
        assertTrue(Color.red(light) > 180)
        assertTrue(Color.red(dark) < 70)
    }

    @Test
    fun noColorGivesGray() {
        val p = solid(Color.rgb(200, 50, 30)).withEffects(Adjustments(saturation = -1f), emptyList()).getPixel(0, 0)
        assertEquals(Color.red(p).toFloat(), Color.green(p).toFloat(), 2f)
        assertEquals(Color.green(p).toFloat(), Color.blue(p).toFloat(), 2f)
    }

    @Test
    fun warmthAddsRedAndRemovesBlue() {
        val p = solid(gray).withEffects(Adjustments(warmth = 1f), emptyList()).getPixel(0, 0)
        assertTrue(Color.red(p) > Color.blue(p))
        val cold = solid(gray).withEffects(Adjustments(warmth = -1f), emptyList()).getPixel(0, 0)
        assertTrue(Color.blue(cold) > Color.red(cold))
    }

    @Test
    fun penDrawsOnlyAlongTheStroke() {
        val stroke = Mark(listOf(Offset(0f, 0.5f), Offset(1f, 0.5f)), width = 0.1f, color = Color.RED, mosaic = false)
        val out = solid(Color.WHITE, 100, 100).withEffects(Adjustments(), listOf(stroke))
        assertEquals(Color.RED, out.getPixel(50, 50))
        assertEquals(Color.WHITE, out.getPixel(50, 10))
    }

    @Test
    fun singleTapLeavesADot() {
        val dot = Mark(listOf(Offset(0.5f, 0.5f)), width = 0.2f, color = Color.BLUE, mosaic = false)
        assertEquals(Color.BLUE, solid(Color.WHITE, 100, 100).withEffects(Adjustments(), listOf(dot)).getPixel(50, 50))
    }

    @Test
    fun mosaicIsMadeOfUniformBlocks() {
        val m = noise().mosaic()
        // 200 px / 40 blocks = 5 px blocks: every pixel of a block has the same color
        val block = m.getPixel(10, 10)
        for (y in 10 until 15) for (x in 10 until 15) assertEquals(block, m.getPixel(x, y))
        assertNotEquals(m.getPixel(10, 10), m.getPixel(100, 100))
    }

    @Test
    fun hideStrokePixelatesOnlyWhereItIsPainted() {
        val src = noise()
        val hide = Mark(listOf(Offset(0.1f, 0.5f), Offset(0.9f, 0.5f)), width = 0.2f, color = 0, mosaic = true)
        val out = src.withEffects(Adjustments(), listOf(hide))
        // Under the stroke: the original detail is gone (neighbours share their block's color)
        var same = 0
        for (x in 50 until 150) if (out.getPixel(x, 100) == out.getPixel(x + 1, 100)) same++
        assertTrue("only $same/100 neighbours equal", same > 60)
        // Away from it: untouched
        assertEquals(src.getPixel(30, 10), out.getPixel(30, 10))
        assertEquals(src.getPixel(170, 190), out.getPixel(170, 190))
    }
}
