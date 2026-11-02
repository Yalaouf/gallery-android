package com.mephilia.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaDetailsTest {
    @Test
    fun formatsAFullExposure() {
        assertEquals(
            "f/1.8 · 1/120 s · ISO 100 · 26 mm",
            formatExposure(fNumber = 1.8, exposureTime = 1 / 120.0, iso = 100, focal35 = 26, focal = 6.9),
        )
    }

    @Test
    fun longExposuresAndRealFocalLength() {
        // Seconds when 1 s or more; the real focal length when the 35 mm one is unknown
        assertEquals("f/8 · 2 s · 4.3 mm", formatExposure(8.0, 2.0, 0, 0, 4.25))
        assertEquals("1.5 s", formatExposure(0.0, 1.5, 0, 0, 0.0))
    }

    @Test
    fun nothingKnownGivesNull() {
        assertNull(formatExposure(0.0, 0.0, 0, 0, 0.0))
    }

    @Test
    fun readsVideoLocations() {
        assertEquals(48.8577 to 2.295, parseIso6709("+48.8577+002.2950/"))
        // With altitude, and southern / western hemispheres
        assertEquals(-33.8568 to -151.2153, parseIso6709("-33.8568-151.2153+010.000/"))
        assertNull(parseIso6709("unknown"))
    }
}
