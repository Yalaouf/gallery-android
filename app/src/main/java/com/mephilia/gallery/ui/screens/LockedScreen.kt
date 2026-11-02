package com.mephilia.gallery.ui.screens

import androidx.activity.compose.LocalActivity
import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.SelectionTopBar
import com.mephilia.gallery.ui.components.deleteLocked
import com.mephilia.gallery.ui.components.rememberSelection
import com.mephilia.gallery.ui.components.restoreFromLocked
import kotlinx.coroutines.launch

/** Hides the window content from screenshots, screen recording and the recent apps screen. */
@Composable
fun SecureWindow() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL

private fun canAuthenticate(context: Context) =
    context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** Asks for the fingerprint, face or screen lock (PIN, pattern, password). */
private fun authenticate(context: Context, vm: GalleryViewModel) {
    vm.authenticating = true
    val prompt = BiometricPrompt.Builder(context)
        .setTitle(context.getString(R.string.locked_title))
        .setSubtitle(context.getString(R.string.locked_prompt))
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    prompt.authenticate(
        CancellationSignal(),
        context.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                vm.authenticating = false
                vm.unlock()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                vm.authenticating = false
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    SecureWindow()
    SystemBars(lightIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f)
    val context = LocalContext.current
    val unlocked by vm.lockedUnlocked.collectAsStateWithLifecycle()
    val items by vm.locked.collectAsStateWithLifecycle()
    val columns by vm.gridColumns.collectAsStateWithLifecycle()
    val selection = rememberSelection(items)
    val scope = rememberCoroutineScope()
    val available = remember { canAuthenticate(context) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Asks right away when opened
    LaunchedEffect(Unit) { if (!unlocked && available) authenticate(context, vm) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection.ids.size, onClose = selection::clear, onSelectAll = { selection.selectAll(items) })
            } else TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                },
                title = { Text(stringResource(R.string.locked_title), fontWeight = FontWeight.Bold) },
                actions = {
                    if (unlocked && items.isNotEmpty()) {
                        IconButton(onClick = { selection.selectAll(items) }) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !unlocked -> LockedGate(
                    available = available,
                    onUnlock = { authenticate(context, vm) },
                )
                items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.locked_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> MediaGrid(
                    items = items,
                    columns = columns,
                    groupByDate = true,
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp,
                    ),
                    onClick = { if (selection.active) selection.toggle(it) else onOpen(it) },
                    selection = selection,
                )
            }
            AnimatedVisibility(
                visible = selection.active,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp).shadow(16.dp, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        LockedBarAction(Icons.Rounded.LockOpen, stringResource(R.string.action_unlock_item)) {
                            val chosen = selection.items(items)
                            scope.launch {
                                restoreFromLocked(context, chosen)
                                selection.clear()
                            }
                        }
                        LockedBarAction(Icons.Rounded.DeleteForever, stringResource(R.string.action_delete_forever)) {
                            confirmDelete = true
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val chosen = selection.items(items)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null) },
            title = { Text(pluralStringResource(R.plurals.delete_forever_title, chosen.size, chosen.size)) },
            text = { Text(stringResource(R.string.delete_locked_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        deleteLocked(context, chosen)
                        selection.clear()
                    }
                }) { Text(stringResource(R.string.action_delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun LockedGate(available: Boolean, onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (available) R.string.locked_explainer else R.string.locked_no_screen_lock),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (available) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text(stringResource(R.string.locked_unlock)) }
        }
    }
}

@Composable
private fun LockedBarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
