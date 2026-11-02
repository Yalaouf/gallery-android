package com.mephilia.gallery.editor

import androidx.compose.ui.geometry.Rect
import com.mephilia.gallery.ui.components.FullCrop
import com.mephilia.gallery.ui.components.centeredCrop
import com.mephilia.gallery.ui.components.rotatedBy
import com.mephilia.gallery.ui.editor.glCrop
import com.mephilia.gallery.ui.editor.straightenScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure geometry behind cropping and straightening (no Android needed). */
class CropMathTest {
    private fun assertRect(expected: Rect, actual: Rect) {
        listOf(expected.left to actual.left, expected.top to actual.top, expected.right to actual.right, expected.bottom to actual.bottom)
            .forEach { (e, a) -> assertEquals("$expected vs $actual", e, a, 1e-5f) }
    }

    @Test
    fun noAngleNoZoom() {
        assertEquals(1f, straightenScale(400f, 300f, 0f))
    }

    @Test
    fun squareAt45DegreesZoomsBySqrt2() {
        assertEquals(1f / sqrt(2f), straightenScale(100f, 100f, 45f), 1e-5f)
        // Same zoom whichever way it's tilted
        assertEquals(straightenScale(400f, 300f, 12f), straightenScale(400f, 300f, -12f))
    }

    @Test
    fun straightenedImageAlwaysCoversTheFrame() {
        // The zoomed frame's corners, rotated back into the image, must stay inside it: no empty corners
        val w = 400f
        val h = 300f
        for (angle in listOf(1f, 7.5f, 20f, 33f, 45f, -45f)) {
            val s = straightenScale(w, h, angle)
            val t = Math.toRadians(angle.toDouble())
            for ((cx, cy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) {
                val x = cx * w * s / 2
                val y = cy * h * s / 2
                val rx = x * cos(t) + y * sin(t)
                val ry = -x * sin(t) + y * cos(t)
                assertTrue("angle $angle corner ($cx,$cy)", kotlin.math.abs(rx) <= w / 2 + 0.01 && kotlin.math.abs(ry) <= h / 2 + 0.01)
            }
        }
    }

    @Test
    fun centeredCropHasTheRequestedRatio() {
        // 1:1 in a 4:3 image: full height, centered horizontally
        val c = centeredCrop(1f, 4f / 3f)
        assertRect(Rect(0.125f, 0f, 0.875f, 1f), c)
        // 16:9 in a 4:3 image: full width, centered vertically; pixel ratio checks out
        val wide = centeredCrop(16f / 9f, 4f / 3f)
        assertEquals(16f / 9f, (wide.width * 4f) / (wide.height * 3f), 1e-4f)
        assertEquals(0f, wide.left)
        assertEquals(FullCrop, centeredCrop(null, 1.5f))
    }

    @Test
    fun cropTurnsWithTheImage() {
        val r = Rect(0.1f, 0.2f, 0.5f, 0.6f)
        // A clockwise turn moves the top-left area to the top-right
        assertRect(Rect(0.4f, 0.1f, 0.8f, 0.5f), r.rotatedBy(1))
        assertRect(r, r.rotatedBy(4))
        assertRect(r.rotatedBy(3), r.rotatedBy(-1))
    }

    @Test
    fun videoCropUsesOpenGlCoordinates() {
        assertEquals(listOf(-1f, 1f, -1f, 1f), glCrop(FullCrop))
        // Top half of the frame: y points up in OpenGL, so it's bottom 0 .. top 1
        assertEquals(listOf(-1f, 1f, 0f, 1f), glCrop(Rect(0f, 0f, 1f, 0.5f)))
        assertEquals(listOf(0f, 1f, -1f, 1f), glCrop(Rect(0.5f, 0f, 1f, 1f)))
    }
}
