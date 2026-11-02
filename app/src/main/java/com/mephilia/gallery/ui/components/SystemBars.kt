package com.mephilia.gallery.ui.components

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Background luminance above which the system bars use dark icons. */
private const val LIGHT_BACKGROUND_LUMINANCE = 0.5f

/**
 * Sets the system bars' appearance for the current screen. Each screen declares its own;
 * during a transition, the entering screen wins over the leaving one.
 *
 * @param lightIcons dark icons for a light background (the name follows Android's "light appearance").
 * @param hidden hides the status and navigation bars; a swipe from the edge shows them for a moment.
 */
@Composable
fun SystemBars(lightIcons: Boolean, hidden: Boolean = false) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    LaunchedEffect(lightIcons, hidden) {
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightIcons
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** System bars for a screen with the theme's background: dark icons on a light background, light ones on a dark one. */
@Composable
fun ThemedSystemBars() {
    SystemBars(lightIcons = MaterialTheme.colorScheme.background.luminance() > LIGHT_BACKGROUND_LUMINANCE)
}
