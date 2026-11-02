package com.mephilia.gallery.ui

import com.mephilia.gallery.R
import com.mephilia.gallery.media
import com.mephilia.gallery.ui.components.isValidFolderName
import com.mephilia.gallery.ui.components.normalizeForSearch
import com.mephilia.gallery.ui.components.formatDuration
import com.mephilia.gallery.ui.components.refusalReason
import com.mephilia.gallery.data.MediaWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRulesTest {
    private val photo = media(1)
    private val video = media(2, video = true)

    @Test
    fun folderNames() {
        assertTrue(isValidFolderName("Vacances été 2026"))
        assertFalse(isValidFolderName(""))
        assertFalse(isValidFolderName("   "))
        assertFalse(isValidFolderName("a/b"))
        assertFalse(isValidFolderName("what?"))
        // Hidden folders would vanish from every gallery
        assertFalse(isValidFolderName(".secret"))
    }

    @Test
    fun androidRefusesPhotosInMoviesAndAnythingInAudioFolders() {
        assertEquals(R.string.folder_no_photos, refusalReason("Movies/Trips/", listOf(photo, video)))
        assertNull(refusalReason("Movies/Trips/", listOf(video)))
        assertEquals(R.string.folder_audio_only, refusalReason("Music/", listOf(video)))
        assertNull(refusalReason("Pictures/Trips/", listOf(photo, video)))
        assertNull(refusalReason("DCIM/Camera/", listOf(photo)))
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals(normalizeForSearch("tele"), normalizeForSearch("Télé"))
        assertTrue(normalizeForSearch("Écran d'accueil").contains(normalizeForSearch("ecran")))
    }

    @Test
    fun copiesKeepTheNameAndGetTheNewExtension() {
        val name = MediaWriter.copyName("IMG_2024.heic", "jpg")
        assertTrue(name, name.startsWith("IMG_2024_edit_") && name.endsWith(".jpg"))
        assertTrue(MediaWriter.copyName("no_extension", "png").startsWith("no_extension_edit_"))
    }

    @Test
    fun durations() {
        assertEquals("0:05", formatDuration(5_400))
        assertEquals("12:34", formatDuration((12 * 60 + 34) * 1000L))
        assertEquals("1:02:03", formatDuration((3600 + 2 * 60 + 3) * 1000L))
    }
}
