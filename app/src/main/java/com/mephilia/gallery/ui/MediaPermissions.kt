package com.mephilia.gallery.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.theme.Spacing

/** Size of the icon of the permission screen. */
private val PERMISSION_ICON_SIZE = 72.dp

/** How much of the user's photos and videos the app can read. */
enum class Access {
    /** Nothing: the permission screen is shown. */
    None,

    /** Only the photos the user picked (Android 14+); a banner offers to give access to all. */
    Partial,

    /** Everything. */
    Full,
}

/**
 * The permissions to ask for to read photos and videos, which depend on the Android version.
 * The media location permission keeps the GPS data in photos, so edited copies don't lose it.
 */
val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

/** How much access the user currently gives to their photos and videos. */
fun currentAccess(context: Context): Access {
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
            if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.Full else Access.None
        granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO) -> Access.Full
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.Partial
        else -> Access.None
    }
}

/** Shown while the app has no access to photos: explains why it needs it, with a button to [onRequest] it. */
@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(PERMISSION_ICON_SIZE), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.s))
            Text(stringResource(R.string.perm_text), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xl))
            Button(onClick = onRequest) { Text(stringResource(R.string.perm_button)) }
        }
    }
}
