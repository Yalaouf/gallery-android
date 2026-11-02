package com.mephilia.gallery.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import com.mephilia.gallery.R

/**
 * The launcher icon choices, with their label in the settings. Each one is a launcher entry (an activity alias
 * in the manifest, named [alias]); the chosen one is enabled and the others disabled. The choice lives in the
 * package manager itself, so it needs no setting.
 */
enum class AppIcon(@StringRes val label: Int, val alias: String) {
    /** Follows the system theme, on launchers that support it. Kept as ".MainActivity" from before 2.0. */
    Auto(R.string.icon_auto, ".MainActivity"),
    Light(R.string.icon_light, ".LauncherLight"),
    Dark(R.string.icon_dark, ".LauncherDark");

    companion object {
        /** The icon in use. Until one is picked, it's the manifest's default: only [Auto] is enabled there. */
        fun current(context: Context): AppIcon {
            val packageManager = context.packageManager
            return entries.firstOrNull { icon ->
                val state = packageManager.getComponentEnabledSetting(component(context, icon))
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == Auto)
            } ?: Auto
        }

        /** Switches to the [choice] icon. The new entry is enabled first, so there's never a moment without an icon. */
        fun set(context: Context, choice: AppIcon) {
            val packageManager = context.packageManager
            fun setEnabled(icon: AppIcon, enabled: Boolean) = packageManager.setComponentEnabledSetting(
                component(context, icon),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            setEnabled(choice, true)
            entries.filter { it != choice }.forEach { setEnabled(it, false) }
        }

        /** The launcher entry of [icon]. */
        private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, context.packageName + icon.alias)
    }
}
