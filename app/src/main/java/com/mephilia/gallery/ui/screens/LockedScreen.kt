package com.mephilia.gallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.BarAction
import com.mephilia.gallery.ui.components.ConfirmDeleteLockedDialog
import com.mephilia.gallery.ui.components.EmptyState
import com.mephilia.gallery.ui.components.FloatingActionBar
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.SelectionTopBar
import com.mephilia.gallery.ui.components.ThemedSystemBars
import com.mephilia.gallery.ui.components.deleteLocked
import com.mephilia.gallery.ui.components.rememberSelection
import com.mephilia.gallery.ui.components.restoreFromLocked
import com.mephilia.gallery.ui.theme.Dimens
import com.mephilia.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Size of the lock icon of the locked folder's gate. */
private val GATE_ICON_SIZE = 64.dp

/**
 * The locked folder: photos and videos hidden from the gallery and from other apps, behind the fingerprint
 * or screen lock. The prompt shows as soon as the screen opens; the window is hidden from screenshots and the
 * recent apps screen. Items can be selected to unhide them or delete them for good.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    SecureWindow()
    ThemedSystemBars()
    val context = LocalContext.current
    val unlocked by vm.lockedUnlocked.collectAsStateWithLifecycle()
    val items by vm.locked.collectAsStateWithLifecycle()
    val columns by vm.gridColumns.collectAsStateWithLifecycle()
    val selection = rememberSelection(items)
    val scope = rememberCoroutineScope()
    val available = remember { canAuthenticate(context) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!unlocked && available) authenticate(context, vm) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection.ids.size, onClose = selection::clear, onSelectAll = { selection.selectAll(items) })
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                    },
                    title = { Text(stringResource(R.string.locked_title), fontWeight = FontWeight.Bold) },
                    actions = {
                        if (unlocked && items.isNotEmpty()) {
                            IconButton(onClick = { selection.selectAll(items) }) {
                                Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all))
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !unlocked -> LockedGate(available = available, onUnlock = { authenticate(context, vm) })
                items.isEmpty() -> EmptyState(Icons.Rounded.Lock, stringResource(R.string.locked_empty))
                else -> MediaGrid(
                    items = items,
                    columns = columns,
                    groupByDate = true,
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Dimens.actionsBarClearance,
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
                FloatingActionBar(
                    Modifier.navigationBarsPadding().padding(bottom = Dimens.floatingBarBottomMargin),
                    horizontalPadding = Spacing.m,
                ) {
                    BarAction(Icons.Rounded.LockOpen, stringResource(R.string.action_unlock_item)) {
                        val chosen = selection.items(items)
                        scope.launch {
                            restoreFromLocked(context, chosen)
                            selection.clear()
                        }
                    }
                    BarAction(Icons.Rounded.DeleteForever, stringResource(R.string.action_delete_forever)) { confirmDelete = true }
                }
            }
        }
    }

    if (confirmDelete) {
        val chosen = selection.items(items)
        ConfirmDeleteLockedDialog(chosen.size, onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            scope.launch {
                deleteLocked(context, chosen)
                selection.clear()
            }
        }
    }
}

/**
 * Shown while the locked folder is locked: what it is, and a button to [onUnlock] it. Without a screen lock
 * on the phone ([available] false), it explains that one is needed instead.
 */
@Composable
private fun LockedGate(available: Boolean, onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(GATE_ICON_SIZE), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Spacing.l))
        Text(
            stringResource(if (available) R.string.locked_explainer else R.string.locked_no_screen_lock),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (available) {
            Spacer(Modifier.height(Spacing.xl))
            Button(onClick = onUnlock) { Text(stringResource(R.string.locked_unlock)) }
        }
    }
}
