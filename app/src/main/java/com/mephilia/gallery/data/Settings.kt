package com.mephilia.gallery.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.mephilia.gallery.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark),
}

data class Settings(
    // Apparence
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    // Démarrage : 0 = Photos, 1 = Albums
    val startTab: Int = 0,
    // Vidéos
    val autoplayVideos: Boolean = true,
    val alwaysMuted: Boolean = false,
    val loopVideos: Boolean = true,
    // Édition
    // Bouton "Enregistrer" : réglable séparément pour les photos et les vidéos
    val saveModePhoto: SaveMode = SaveMode.Ask,
    val saveModeVideo: SaveMode = SaveMode.Ask,
    val jpegQuality: Int = 95,
    val keepLocation: Boolean = true,
    // Suppression
    val confirmDelete: Boolean = true,
)

/** Réglages de l'application, partagés par tous les écrans et enregistrés à chaque modification. */
object AppSettings {
    private lateinit var sp: SharedPreferences
    private val state = MutableStateFlow(Settings())
    val flow: StateFlow<Settings> = state.asStateFlow()
    val current: Settings get() = state.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val d = Settings()
        state.value = Settings(
            theme = ThemeMode.entries.firstOrNull { it.name == sp.getString("theme", null) } ?: d.theme,
            dynamicColor = sp.getBoolean("dynamic_color", d.dynamicColor),
            pureBlack = sp.getBoolean("pure_black", d.pureBlack),
            startTab = sp.getInt("start_tab", d.startTab),
            autoplayVideos = sp.getBoolean("autoplay", d.autoplayVideos),
            alwaysMuted = sp.getBoolean("always_muted", d.alwaysMuted),
            loopVideos = sp.getBoolean("loop", d.loopVideos),
            // L'ancien réglage commun ("save_mode") sert de valeur initiale aux deux nouveaux
            saveModePhoto = saveMode(sp.getString("save_mode_photo", null) ?: legacySaveMode(context)) ?: d.saveModePhoto,
            saveModeVideo = saveMode(sp.getString("save_mode_video", null) ?: legacySaveMode(context)) ?: d.saveModeVideo,
            jpegQuality = sp.getInt("jpeg_quality", d.jpegQuality),
            keepLocation = sp.getBoolean("keep_location", d.keepLocation),
            confirmDelete = sp.getBoolean("confirm_delete", d.confirmDelete),
        )
    }

    /** Mode d'enregistrement selon le type de média. */
    fun saveModeFor(isVideo: Boolean) = if (isVideo) current.saveModeVideo else current.saveModePhoto

    fun setSaveModeFor(isVideo: Boolean, mode: SaveMode) = update {
        if (isVideo) it.copy(saveModeVideo = mode) else it.copy(saveModePhoto = mode)
    }

    private fun saveMode(name: String?) = SaveMode.entries.firstOrNull { it.name == name }

    private fun legacySaveMode(context: Context) = sp.getString("save_mode", null)
        ?: context.getSharedPreferences("gallery", Context.MODE_PRIVATE).getString("save_mode", null)

    fun update(transform: (Settings) -> Settings) {
        state.update(transform)
        val s = state.value
        sp.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamic_color", s.dynamicColor)
            .putBoolean("pure_black", s.pureBlack)
            .putInt("start_tab", s.startTab)
            .putBoolean("autoplay", s.autoplayVideos)
            .putBoolean("always_muted", s.alwaysMuted)
            .putBoolean("loop", s.loopVideos)
            .putString("save_mode_photo", s.saveModePhoto.name)
            .putString("save_mode_video", s.saveModeVideo.name)
            .putInt("jpeg_quality", s.jpegQuality)
            .putBoolean("keep_location", s.keepLocation)
            .putBoolean("confirm_delete", s.confirmDelete)
            .apply()
    }
}
