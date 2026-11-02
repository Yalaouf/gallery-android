package com.mephilia.gallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.ThemeMode

/** Background of the dark theme: nearly black, slightly blue. */
private val DARK_BACKGROUND = Color(0xFF0E0E12)

/** The dark theme's colors, when wallpaper colors are off or unavailable. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A6FF),
    onPrimary = Color(0xFF24005B),
    primaryContainer = Color(0xFF3B2A7A),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = Color(0xFF7FD8D0),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF1E4B47),
    onSecondaryContainer = Color(0xFFA0F2EA),
    background = DARK_BACKGROUND,
    onBackground = Color(0xFFE6E1E9),
    surface = DARK_BACKGROUND,
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF2A2833),
    onSurfaceVariant = Color(0xFFC9C4D2),
    surfaceContainerLowest = Color(0xFF09090C),
    surfaceContainerLow = Color(0xFF16151B),
    surfaceContainer = Color(0xFF1B1A21),
    surfaceContainerHigh = Color(0xFF25242C),
    surfaceContainerHighest = Color(0xFF302F37),
    outline = Color(0xFF938F9C),
)

/** The light theme's colors, when wallpaper colors are off or unavailable. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B45C4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DDFF),
    onPrimaryContainer = Color(0xFF1B0063),
    secondary = Color(0xFF006A63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA0F2EA),
    onSecondaryContainer = Color(0xFF00201E),
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
)

/** Corner radii of the app's components, rounder than Material's defaults. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * The app's theme, from the settings: light, dark or following the system; the wallpaper's colors (Android 12+)
 * or the app's own; and a pure black background in the dark theme if chosen. Wallpaper colors keep the app's
 * dark background, which is deeper than Material's.
 */
@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    val settings by AppSettings.flow.collectAsState()
    val darkTheme = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val dynamicColor = settings.dynamicColor
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx).copy(background = DARK_BACKGROUND, surface = DARK_BACKGROUND)
            else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colors = if (darkTheme && settings.pureBlack) base.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0D),
        surfaceContainer = Color(0xFF111114),
    ) else base
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
