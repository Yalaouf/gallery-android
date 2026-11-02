package com.mephilia.gallery.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.mephilia.gallery.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Name of the settings file. */
private const val FILE_NAME = "settings"

/** Name of the view choices file, where versions before 2.0 kept the save mode. */
private const val LEGACY_FILE_NAME = "gallery"

/** Preference key of [Settings.theme]. */
private const val KEY_THEME = "theme"

/** Preference key of [Settings.dynamicColor]. */
private const val KEY_DYNAMIC_COLOR = "dynamic_color"

/** Preference key of [Settings.pureBlack]. */
private const val KEY_PURE_BLACK = "pure_black"

/** Preference key of [Settings.startTab]. */
private const val KEY_START_TAB = "start_tab"

/** Preference key of [Settings.autoplayVideos]. */
private const val KEY_AUTOPLAY = "autoplay"

/** Preference key of [Settings.alwaysMuted]. */
private const val KEY_ALWAYS_MUTED = "always_muted"

/** Preference key of [Settings.loopVideos]. */
private const val KEY_LOOP = "loop"

/** Preference key of [Settings.saveModePhoto]. */
private const val KEY_SAVE_MODE_PHOTO = "save_mode_photo"

/** Preference key of [Settings.saveModeVideo]. */
private const val KEY_SAVE_MODE_VIDEO = "save_mode_video"

/** Preference key of [Settings.jpegQuality]. */
private const val KEY_JPEG_QUALITY = "jpeg_quality"

/** Preference key of [Settings.keepLocation]. */
private const val KEY_KEEP_LOCATION = "keep_location"

/** Preference key of [Settings.confirmDelete]. */
private const val KEY_CONFIRM_DELETE = "confirm_delete"

/** Preference key of [Settings.showTrash]. */
private const val KEY_SHOW_TRASH = "show_trash"

/** Preference key of [Settings.stripLocationOnShare]. */
private const val KEY_STRIP_LOCATION_SHARE = "strip_location_share"

/** Preference key of [Settings.slideshowSeconds]. */
private const val KEY_SLIDESHOW_SECONDS = "slideshow_seconds"

/** Preference key of [Settings.showOnThisDay]. */
private const val KEY_ON_THIS_DAY = "on_this_day"

/** The single save mode of older versions, which seeds both the photo and the video one. */
private const val KEY_LEGACY_SAVE_MODE = "save_mode"

/** JPEG quality of edited photos until the user picks another. */
const val DEFAULT_JPEG_QUALITY = 95

/** Seconds each item shows in a slideshow until the user picks another. */
const val DEFAULT_SLIDESHOW_SECONDS = 4

/** The app's theme, with its label in the settings. */
enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark),
}

/**
 * The settings chosen in the settings screen.
 *
 * @param dynamicColor uses the wallpaper's colors (Android 12+).
 * @param pureBlack a black background in the dark theme.
 * @param startTab the home tab shown at launch: 0 for photos, 1 for albums, 2 for collections.
 * @param alwaysMuted videos start without sound.
 * @param saveModePhoto what saving an edited photo does.
 * @param saveModeVideo what saving an edited video does.
 * @param keepLocation keeps the location in edited photos.
 * @param confirmDelete asks before moving items to the trash.
 * @param showTrash shows the trash in the collections.
 * @param stripLocationOnShare shares photos as copies without their location.
 * @param slideshowSeconds how long each item shows in a slideshow.
 * @param showOnThisDay shows "on this day" at the top of the photos.
 */
data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val startTab: Int = 0,
    val autoplayVideos: Boolean = true,
    val alwaysMuted: Boolean = false,
    val loopVideos: Boolean = true,
    val saveModePhoto: SaveMode = SaveMode.Ask,
    val saveModeVideo: SaveMode = SaveMode.Ask,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    val keepLocation: Boolean = true,
    val confirmDelete: Boolean = true,
    val showTrash: Boolean = true,
    val stripLocationOnShare: Boolean = false,
    val slideshowSeconds: Int = DEFAULT_SLIDESHOW_SECONDS,
    val showOnThisDay: Boolean = true,
)

/** The app's settings, shared by every screen and saved on every change. Call [init] before using them. */
object AppSettings {
    /** Where the settings are stored; set by [init]. */
    private lateinit var preferences: SharedPreferences

    /** The settings, as they change. */
    private val state = MutableStateFlow(Settings())

    /** The settings, updated as they change. */
    val flow: StateFlow<Settings> = state.asStateFlow()

    /** The settings now. */
    val current: Settings get() = state.value

    /** Loads the saved settings. The single save mode of older versions seeds both the photo and the video one. */
    fun init(context: Context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val defaults = Settings()
        val legacySaveMode = legacySaveMode(context)
        state.value = with(preferences) {
            Settings(
                theme = ThemeMode.entries.firstOrNull { it.name == getString(KEY_THEME, null) } ?: defaults.theme,
                dynamicColor = getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColor),
                pureBlack = getBoolean(KEY_PURE_BLACK, defaults.pureBlack),
                startTab = getInt(KEY_START_TAB, defaults.startTab),
                autoplayVideos = getBoolean(KEY_AUTOPLAY, defaults.autoplayVideos),
                alwaysMuted = getBoolean(KEY_ALWAYS_MUTED, defaults.alwaysMuted),
                loopVideos = getBoolean(KEY_LOOP, defaults.loopVideos),
                saveModePhoto = saveMode(getString(KEY_SAVE_MODE_PHOTO, null) ?: legacySaveMode) ?: defaults.saveModePhoto,
                saveModeVideo = saveMode(getString(KEY_SAVE_MODE_VIDEO, null) ?: legacySaveMode) ?: defaults.saveModeVideo,
                jpegQuality = getInt(KEY_JPEG_QUALITY, defaults.jpegQuality),
                keepLocation = getBoolean(KEY_KEEP_LOCATION, defaults.keepLocation),
                confirmDelete = getBoolean(KEY_CONFIRM_DELETE, defaults.confirmDelete),
                showTrash = getBoolean(KEY_SHOW_TRASH, defaults.showTrash),
                stripLocationOnShare = getBoolean(KEY_STRIP_LOCATION_SHARE, defaults.stripLocationOnShare),
                slideshowSeconds = getInt(KEY_SLIDESHOW_SECONDS, defaults.slideshowSeconds),
                showOnThisDay = getBoolean(KEY_ON_THIS_DAY, defaults.showOnThisDay),
            )
        }
    }

    /** What saving an edited photo, or video if [isVideo], does. */
    fun saveModeFor(isVideo: Boolean) = if (isVideo) current.saveModeVideo else current.saveModePhoto

    /** Sets what saving an edited photo, or video if [isVideo], does. */
    fun setSaveModeFor(isVideo: Boolean, mode: SaveMode) = update {
        if (isVideo) it.copy(saveModeVideo = mode) else it.copy(saveModePhoto = mode)
    }

    /** Changes the settings with [transform] and saves them. */
    fun update(transform: (Settings) -> Settings) {
        state.update(transform)
        val settings = state.value
        preferences.edit()
            .putString(KEY_THEME, settings.theme.name)
            .putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
            .putBoolean(KEY_PURE_BLACK, settings.pureBlack)
            .putInt(KEY_START_TAB, settings.startTab)
            .putBoolean(KEY_AUTOPLAY, settings.autoplayVideos)
            .putBoolean(KEY_ALWAYS_MUTED, settings.alwaysMuted)
            .putBoolean(KEY_LOOP, settings.loopVideos)
            .putString(KEY_SAVE_MODE_PHOTO, settings.saveModePhoto.name)
            .putString(KEY_SAVE_MODE_VIDEO, settings.saveModeVideo.name)
            .putInt(KEY_JPEG_QUALITY, settings.jpegQuality)
            .putBoolean(KEY_KEEP_LOCATION, settings.keepLocation)
            .putBoolean(KEY_CONFIRM_DELETE, settings.confirmDelete)
            .putBoolean(KEY_SHOW_TRASH, settings.showTrash)
            .putBoolean(KEY_STRIP_LOCATION_SHARE, settings.stripLocationOnShare)
            .putInt(KEY_SLIDESHOW_SECONDS, settings.slideshowSeconds)
            .putBoolean(KEY_ON_THIS_DAY, settings.showOnThisDay)
            .apply()
    }

    /** The save mode called [name], or null if there is none. */
    private fun saveMode(name: String?) = SaveMode.entries.firstOrNull { it.name == name }

    /** The single save mode of older versions, from either of the files it was kept in. */
    private fun legacySaveMode(context: Context) = preferences.getString(KEY_LEGACY_SAVE_MODE, null)
        ?: context.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE).getString(KEY_LEGACY_SAVE_MODE, null)
}
