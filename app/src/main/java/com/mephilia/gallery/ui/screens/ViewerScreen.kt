package com.mephilia.gallery.ui.screens

import android.app.Activity
import android.text.format.Formatter
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.Thumb
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.ConfirmTrashDialog
import com.mephilia.gallery.ui.components.MoveFlow
import com.mephilia.gallery.ui.components.rememberMediaAccess
import com.mephilia.gallery.ui.components.Zoomable
import com.mephilia.gallery.ui.components.shareMedia
import com.mephilia.gallery.ui.components.createPlayer
import com.mephilia.gallery.ui.components.isInMultiWindow
import com.mephilia.gallery.ui.components.setMuted
import com.mephilia.gallery.ui.components.formatDuration
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import kotlinx.coroutines.flow.flowOf
import com.mephilia.gallery.data.MediaEvents

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    bucketId: Long?,
    startId: Long,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
    /** Shown instead of the gallery, e.g. a file opened from another app. */
    items: List<MediaItem>? = null,
    /** Trash items: only restore / delete for good are offered. */
    trashMode: Boolean = false,
) {
    val source: Flow<List<MediaItem>> = remember(bucketId, items) {
        items?.let { flowOf(it) } ?: bucketId?.let { vm.albumMedia(it) } ?: vm.media
    }
    val shown by source.collectAsStateWithLifecycle(initialValue = null)
    val list = shown ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val pager = rememberPagerState(initialPage = list.indexOfFirst { it.id == startId }.coerceAtLeast(0)) { list.size }
    var currentId by remember { mutableStateOf(startId) }
    LaunchedEffect(pager.settledPage, list) {
        list.getOrNull(pager.settledPage)?.let { currentId = it.id }
    }
    // When the list changes (edit, delete), stay on the same item if it still exists
    LaunchedEffect(list) {
        val idx = list.indexOfFirst { it.id == currentId }
        if (idx >= 0 && idx != pager.currentPage) pager.scrollToPage(idx)
    }

    var showBars by remember { mutableStateOf(true) }
    // In split screen, videos start muted so the other app keeps playing
    val multiWindow = isInMultiWindow()
    var muted by remember { mutableStateOf(multiWindow || AppSettings.current.alwaysMuted) }
    LaunchedEffect(multiWindow) { if (multiWindow) muted = true }
    var showInfo by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val albums by vm.albums.collectAsStateWithLifecycle()
    val current = list.getOrNull(pager.currentPage.coerceIn(0, list.lastIndex)) ?: list.first()
    val context = LocalContext.current

    SystemBars(lightIcons = false, hidden = !showBars)

    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            key = { list.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = list[page]
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    active = pager.settledPage == page,
                    muted = muted,
                    onControllerVisibility = { showBars = it },
                )
            } else {
                PhotoPage(item = item, onTap = { showBars = !showBars })
            }
        }

        AnimatedVisibility(showBars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(current.dateTaken)),
                        color = Color.White, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        current.name, color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (current.isVideo) {
                    IconButton(onClick = { muted = !muted }) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            stringResource(if (muted) R.string.sound_on else R.string.sound_off),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        if (trashMode) AnimatedVisibility(showBars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            TrashActionsBar(
                items = listOf(current),
                access = access,
                onSelectAll = null,
                onDone = {},
                modifier = Modifier.navigationBarsPadding().padding(bottom = if (current.isVideo) 80.dp else 16.dp),
            )
        } else AnimatedVisibility(showBars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .navigationBarsPadding()
                    .padding(top = 24.dp, bottom = if (current.isVideo) 72.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ViewerAction(Icons.Rounded.Share, stringResource(R.string.action_share)) { shareMedia(context, listOf(current)) }
                ViewerAction(Icons.Rounded.Edit, stringResource(R.string.action_edit)) { onEdit(current) }
                ViewerAction(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.action_move)) { showMove = true }
                ViewerAction(Icons.Rounded.Info, stringResource(R.string.action_info)) { showInfo = true }
                ViewerAction(Icons.Rounded.Delete, stringResource(R.string.action_delete)) {
                    if (AppSettings.current.confirmDelete) confirmTrash = true
                    else scope.launch { if (access.trash(listOf(current.uri))) MediaEvents.removed(listOf(current.id)) }
                }
            }
        }
    }

    if (confirmTrash) {
        ConfirmTrashDialog(1, onDismiss = { confirmTrash = false }) {
            confirmTrash = false
            scope.launch { if (access.trash(listOf(current.uri))) MediaEvents.removed(listOf(current.id)) }
        }
    }

    if (showMove) {
        MoveFlow(listOf(current), albums, onDismiss = { showMove = false }, onMoved = { showMove = false })
    }

    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }) {
            Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
                Text(stringResource(R.string.details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                InfoRow(stringResource(R.string.info_name), current.name)
                InfoRow(stringResource(R.string.info_date), DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(current.dateTaken)))
                InfoRow(stringResource(R.string.info_folder), current.relativePath.ifBlank { current.bucketName })
                if (current.width > 0) InfoRow(stringResource(R.string.info_resolution), "${current.width} × ${current.height}")
                InfoRow(stringResource(R.string.info_size), Formatter.formatFileSize(context, current.size))
                if (current.isVideo) InfoRow(stringResource(R.string.info_duration), formatDuration(current.durationMs))
                InfoRow(stringResource(R.string.info_type), current.mimeType)
            }
        }
    }
}

@Composable
private fun PhotoPage(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    // Load at ~2× the screen size to keep detail when zooming
    val maxPx = with(density) { (maxOf(config.screenWidthDp, config.screenHeightDp).dp.toPx() * 2).toInt() }
        .coerceAtMost(4096)
    var loaded by remember(item.id) { mutableStateOf(false) }
    // After the file is replaced, keep showing the previous version until the new one is ready (no black frame)
    // Saved state, because the viewer is rebuilt when coming back from the editor
    var shownKey by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    val key = "full:${item.uri}@${item.dateModified}"
    Zoomable(Modifier.fillMaxSize(), onTap = onTap) {
        if (!loaded) {
            AsyncImage(
                model = Thumb(item),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AsyncImage(
            model = remember(key, maxPx) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .memoryCacheKey(key)
                    .placeholderMemoryCacheKey(shownKey)
                    .size(maxPx)
                    .build()
            },
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            onState = {
                if (it is AsyncImagePainter.State.Success) {
                    loaded = true
                    shownKey = key
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(item: MediaItem, active: Boolean, muted: Boolean, onControllerVisibility: (Boolean) -> Unit) {
    if (!active) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(Thumb(item), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
        }
        return
    }
    val context = LocalContext.current
    val player = remember(item.id, item.dateModified) {
        val settings = AppSettings.current
        createPlayer(context, item.uri, muted).apply {
            playWhenReady = settings.autoplayVideos
            repeatMode = if (settings.loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, muted) { player.setMuted(muted) }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                setShowNextButton(false)
                setShowPreviousButton(false)
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                    onControllerVisibility(v == View.VISIBLE)
                })
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * System bar appearance for the current screen. Each screen declares its own;
 * during a transition, the entering screen wins over the leaving one.
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
