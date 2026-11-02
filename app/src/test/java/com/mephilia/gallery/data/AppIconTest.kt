package com.mephilia.gallery.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The icon choice must always leave exactly one launcher entry. */
@RunWith(RobolectricTestRunner::class)
class AppIconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager

    private fun launcherEntries(): List<String> =
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0,
        ).map { it.activityInfo.name.substringAfterLast('.') }

    @Test
    fun automaticByDefaultWithTheOriginalEntry() {
        assertEquals(AppIcon.Auto, AppIcon.current(context))
        // Same component as before 2.0, so existing home screen icons keep working
        assertEquals(listOf("MainActivity"), launcherEntries())
    }

    @Test
    fun switchingKeepsExactlyOneEntry() {
        for (choice in listOf(AppIcon.Dark, AppIcon.Light, AppIcon.Auto, AppIcon.Dark)) {
            AppIcon.set(context, choice)
            assertEquals(choice, AppIcon.current(context))
            assertEquals(listOf(choice.alias.removePrefix(".")), launcherEntries())
        }
        val dark = ComponentName(context, context.packageName + ".LauncherDark")
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, pm.getComponentEnabledSetting(dark))
    }
}
