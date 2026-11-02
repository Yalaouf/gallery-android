package com.mephilia.gallery.editor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import com.mephilia.gallery.ui.editor.PhotoEdits
import com.mephilia.gallery.ui.editor.applyEdits
import com.mephilia.gallery.ui.editor.transformed
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Renders edits on a 40×20 image made of four colored quadrants and checks where each color ends up:
 * red top-left, green top-right, blue bottom-left, yellow bottom-right.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEditsTest {
    private val w = 40
    private val h = 20
    private val source: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until h) for (x in 0 until w) {
            setPixel(
                x, y,
                when {
                    x < w / 2 && y < h / 2 -> Color.RED
                    x >= w / 2 && y < h / 2 -> Color.GREEN
                    x < w / 2 -> Color.BLUE
                    else -> Color.YELLOW
                },
            )
        }
    }

    /** Color in the middle of each quadrant of the result: top-left, top-right, bottom-left, bottom-right. */
    private fun quadrants(b: Bitmap) = listOf(
        b.getPixel(b.width / 4, b.height / 4), b.getPixel(3 * b.width / 4, b.height / 4),
        b.getPixel(b.width / 4, 3 * b.height / 4), b.getPixel(3 * b.width / 4, 3 * b.height / 4),
    )

    @Test
    fun noEditKeepsTheImage() {
        val out = source.applyEdits(PhotoEdits())
        assertEquals(w to h, out.width to out.height)
        assertEquals(listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW), quadrants(out))
    }

    @Test
    fun cropKeepsTheChosenArea() {
        val out = source.applyEdits(PhotoEdits(crop = Rect(0.5f, 0f, 1f, 1f)))
        assertEquals(20 to 20, out.width to out.height)
        assertEquals(listOf(Color.GREEN, Color.GREEN, Color.YELLOW, Color.YELLOW), quadrants(out))
    }

    @Test
    fun clockwiseTurn() {
        val out = source.applyEdits(PhotoEdits(quarterTurns = 1))
        assertEquals(20 to 40, out.width to out.height)
        // Turning clockwise brings the bottom-left (blue) to the top-left
        assertEquals(listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN), quadrants(out))
    }

    @Test
    fun mirror() {
        val out = source.applyEdits(PhotoEdits(flipH = true))
        assertEquals(listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE), quadrants(out))
    }

    @Test
    fun cropIsInTheTurnedImage() {
        // The crop is drawn on the turned preview: its top half is the original's left half (red and blue)
        val out = source.applyEdits(PhotoEdits(quarterTurns = 1, crop = Rect(0f, 0f, 1f, 0.5f)))
        assertEquals(20 to 20, out.width to out.height)
        assertEquals(listOf(Color.BLUE, Color.RED, Color.BLUE, Color.RED), quadrants(out))
    }

    @Test
    fun cropInTheTurnedAndMirroredImage() {
        val edits = PhotoEdits(quarterTurns = 1, flipH = true, crop = Rect(0f, 0f, 1f, 0.5f))
        val out = source.applyEdits(edits)
        // Turned then mirrored: red top-left, blue top-right in the top half
        assertEquals(listOf(Color.RED, Color.BLUE, Color.RED, Color.BLUE), quadrants(out))
        // The preview transform agrees with the final render
        assertEquals(source.transformed(edits).getPixel(0, 0), out.getPixel(0, 0))
    }

    @Test
    fun straighteningLeavesNoEmptyCorners() {
        // Large enough that rounding to whole pixels doesn't skew the proportions
        val big = Bitmap.createScaledBitmap(source, 400, 200, false)
        for (angle in listOf(-30f, 5f, 45f)) {
            val out = big.applyEdits(PhotoEdits(angle = angle))
            for ((x, y) in listOf(0 to 0, out.width - 1 to 0, 0 to out.height - 1, out.width - 1 to out.height - 1)) {
                assertEquals("angle $angle at ($x,$y)", 255, Color.alpha(out.getPixel(x, y)))
            }
            // Zoomed in to stay covered: smaller than the source, same proportions
            assertEquals(2f, out.width.toFloat() / out.height, 0.02f)
        }
    }
}
