package com.mephilia.gallery.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everySettingSurvivesARestart() {
        AppSettings.init(context)
        val changed = Settings(
            theme = ThemeMode.Dark, dynamicColor = false, pureBlack = true, startTab = 2,
            autoplayVideos = false, alwaysMuted = true, loopVideos = false,
            saveModePhoto = SaveMode.Replace, saveModeVideo = SaveMode.Copy, jpegQuality = 80,
            keepLocation = false, confirmDelete = false, showTrash = false,
            stripLocationOnShare = true, slideshowSeconds = 7, showOnThisDay = false,
        )
        AppSettings.update { changed }
        // Read back from storage, as on the next launch
        AppSettings.init(context)
        assertEquals(changed, AppSettings.current)
    }

    @Test
    fun defaultsOnFirstLaunch() {
        AppSettings.init(context)
        assertEquals(Settings(), AppSettings.current)
    }

    @Test
    fun oldSharedSaveModeSeedsBothNewOnes() {
        // Before 1.1 there was a single "save_mode" for photos and videos
        context.getSharedPreferences("gallery", Context.MODE_PRIVATE).edit().putString("save_mode", "Copy").commit()
        AppSettings.init(context)
        assertEquals(SaveMode.Copy, AppSettings.current.saveModePhoto)
        assertEquals(SaveMode.Copy, AppSettings.current.saveModeVideo)
    }

    @Test
    fun rememberedSaveChoiceOnlyAppliesToItsType() {
        AppSettings.init(context)
        AppSettings.setSaveModeFor(isVideo = true, SaveMode.Replace)
        assertEquals(SaveMode.Replace, AppSettings.saveModeFor(isVideo = true))
        assertEquals(SaveMode.Ask, AppSettings.saveModeFor(isVideo = false))
    }
}
