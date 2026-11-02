package com.mephilia.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.mephilia.gallery.media
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** "Remove location when sharing": the shared copy has no GPS data, and keeps the rest. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCopiesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetFileProvider() {
        // FileProvider remembers the cache folder's path, which changes for every Robolectric test
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let {
            (it.get(null) as MutableMap<*, *>).clear()
        }
    }

    private fun photoWithLocation(name: String): File {
        val f = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(f).apply {
            setLatLong(48.8584, 2.2945)
            setAttribute(ExifInterface.TAG_MAKE, "Google")
            setAttribute(ExifInterface.TAG_MODEL, "Pixel 9")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:07:14 10:00:00")
            saveAttributes()
        }
        assertNotNull("test setup", ExifInterface(f).latLong)
        return f
    }

    /** The copy that was prepared for [name], in the app's share folder. */
    private fun sharedCopy(name: String): File =
        File(context.cacheDir, "shared").walkTopDown().single { it.isFile && it.name == name }

    @Test
    fun jpegLosesItsLocationButKeepsTheRest() = runBlocking {
        val original = photoWithLocation("trip.jpg")
        val item = media(1, name = "trip.jpg", uri = Uri.fromFile(original))
        val shared = ShareCopies.prepare(context, listOf(item)).single()

        assertNotNull(shared)
        assertNotEquals(item.uri, shared)
        val exif = ExifInterface(sharedCopy("trip.jpg"))
        assertNull(exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals("Pixel 9", exif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("2026:07:14 10:00:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        // The original is untouched
        assertNotNull(ExifInterface(original).latLong)
    }

    @Test
    fun otherFormatsAreConvertedToJpegWithoutMetadata() = runBlocking {
        // A format that can't be edited in place (declared as HEIC) is re-encoded
        val original = photoWithLocation("burst.heic")
        val item = media(2, name = "burst.heic", uri = Uri.fromFile(original)).copy(mimeType = "image/heic")
        ShareCopies.prepare(context, listOf(item))
        val copy = sharedCopy("burst.jpg")
        assertNull(ExifInterface(copy).latLong)
    }

    @Test
    fun videosAreSharedAsTheyAre() = runBlocking {
        val video = media(3, video = true)
        assertEquals(listOf(video.uri), ShareCopies.prepare(context, listOf(video)))
    }

    @Test
    fun unreadablePhotoIsNeverSharedWithItsLocation() = runBlocking {
        // If the clean copy can't be made, the original (which has the location) must not be used instead
        val missing = media(4, name = "gone.jpg", uri = Uri.fromFile(File(context.cacheDir, "gone.jpg")))
        val video = media(5, video = true)
        assertEquals(listOf(null, video.uri), ShareCopies.prepare(context, listOf(missing, video)))
    }
}
