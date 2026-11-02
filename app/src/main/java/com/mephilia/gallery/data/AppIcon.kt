package com.mephilia.gallery.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import com.mephilia.gallery.R

/**
 * Launcher icon choice. Each one is a launcher entry (activity alias in the manifest); the chosen one is
 * enabled and the others disabled. The state lives in the package manager itself, so it needs no setting.
 */
enum class AppIcon(@StringRes val label: Int, val alias: String) {
    Auto(R.string.icon_auto, ".MainActivity"),
    Light(R.string.icon_light, ".LauncherLight"),
    Dark(R.string.icon_dark, ".LauncherDark");

    companion object {
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull { icon ->
                val state = pm.getComponentEnabledSetting(component(context, icon))
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    // Not changed yet: the manifest default (only Auto is enabled there)
                    (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == Auto)
            } ?: Auto
        }

        fun set(context: Context, choice: AppIcon) {
            val pm = context.packageManager
            // Enable the new entry first, so there's never a moment without any launcher icon
            pm.setComponentEnabledSetting(
                component(context, choice), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != choice }.forEach {
                pm.setComponentEnabledSetting(
                    component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
                )
            }
        }

        private fun component(context: Context, icon: AppIcon) =
            ComponentName(context.packageName, context.packageName + icon.alias)
    }
}
