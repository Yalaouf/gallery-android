package com.mephilia.gallery.ui.screens.viewer

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.mephilia.gallery.R
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.Thumb
import com.mephilia.gallery.ui.components.createPlayer
import com.mephilia.gallery.ui.components.formatDuration
import com.mephilia.gallery.ui.components.setMuted
import com.mephilia.gallery.ui.theme.Spacing
import kotlinx.coroutines.delay

/** How often the progress bar and the resume position are updated while playing. */
private const val PROGRESS_UPDATE_MS = 200L

/** How long the controls stay visible during playback without any interaction. */
private const val CONTROLS_AUTO_HIDE_MS = 3000L

/** Size of the round play button shown over a video that isn't the current page. */
private val POSTER_BUTTON_SIZE = 64.dp

/** Size of the play icon in [POSTER_BUTTON_SIZE]. */
private val POSTER_ICON_SIZE = 40.dp

/** Size of the round play / pause button in the middle of a playing video. */
private val PLAY_BUTTON_SIZE = 72.dp

/** Size of the play / pause icon in [PLAY_BUTTON_SIZE]. */
private val PLAY_ICON_SIZE = 44.dp

/** Opacity of the dark circle behind the play buttons. */
private const val BUTTON_BACKGROUND_ALPHA = 0.5f

/** Opacity of the unplayed part of the progress bar. */
private const val INACTIVE_TRACK_ALPHA = 0.3f

/**
 * A video in the viewer, with its own controls (play/pause, progress, time) instead of PlayerView's: they are
 * laid out right above the viewer's action bar ([controlsBottomPadding]), so the two can never overlap.
 *
 * Only the [active] page has a player; the others show the video's thumbnail. Where the video was and whether the
 * user paused it are saved, so a new player (screen rotation, theme change…) picks up from there instead of
 * restarting. The controls hide by themselves after a few seconds of playback without interaction ([onAutoHide]).
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPage(
    item: MediaItem,
    active: Boolean,
    muted: Boolean,
    showControls: Boolean,
    controlsBottomPadding: Dp,
    onTap: () -> Unit,
    onAutoHide: () -> Unit,
) {
    var resumeAt by rememberSaveable(item.id, item.dateModified) { mutableLongStateOf(0L) }
    var userPaused by rememberSaveable(item.id, item.dateModified) { mutableStateOf(false) }
    if (!active) {
        VideoPoster(item)
        return
    }
    val context = LocalContext.current
    val player = remember(item.id, item.dateModified) {
        val settings = AppSettings.current
        createPlayer(context, item.uri, muted).apply {
            if (resumeAt > 0) seekTo(resumeAt)
            playWhenReady = settings.autoplayVideos && !userPaused
            repeatMode = if (settings.loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, muted) { player.setMuted(muted) }

    val isPlaying = rememberIsPlaying(player)
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    var interactions by remember { mutableIntStateOf(0) }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            resumeAt = position
            delay(PROGRESS_UPDATE_MS)
        }
    }
    LaunchedEffect(showControls, isPlaying, interactions, dragPosition) {
        if (showControls && isPlaying && dragPosition == null) {
            delay(CONTROLS_AUTO_HIDE_MS)
            onAutoHide()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        )
        AnimatedVisibility(showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            PlayPauseButton(isPlaying) {
                interactions++
                if (player.isPlaying) {
                    player.pause()
                    userPaused = true
                } else {
                    userPaused = false
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                    player.play()
                }
            }
        }
        AnimatedVisibility(
            showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = controlsBottomPadding),
        ) {
            VideoProgressBar(
                position = dragPosition ?: position,
                duration = duration,
                onSeek = { target ->
                    if (dragPosition == null) player.setScrubbingModeEnabled(true)
                    dragPosition = target
                    player.seekTo(target)
                },
                onSeekFinished = {
                    player.setScrubbingModeEnabled(false)
                    dragPosition = null
                    interactions++
                },
            )
        }
    }
}

/** Whether [player] is currently playing, updated as it starts and stops. */
@Composable
private fun rememberIsPlaying(player: ExoPlayer): Boolean {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return isPlaying
}

/** A video that isn't the current page: its thumbnail with a play symbol over it. */
@Composable
private fun VideoPoster(item: MediaItem) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(Thumb(item), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .size(POSTER_BUTTON_SIZE)
                .background(Color.Black.copy(alpha = BUTTON_BACKGROUND_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(POSTER_ICON_SIZE)) }
    }
}

/** The round play / pause button in the middle of the video. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(PLAY_BUTTON_SIZE)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = BUTTON_BACKGROUND_ALPHA))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (isPlaying) R.string.pause else R.string.play),
            tint = Color.White,
            modifier = Modifier.size(PLAY_ICON_SIZE),
        )
    }
}

/**
 * The elapsed time, a draggable progress bar and the total time, all in milliseconds.
 * [onSeek] is called continuously while dragging, then [onSeekFinished] once when the bar is released.
 */
@Composable
private fun VideoProgressBar(position: Long, duration: Long, onSeek: (Long) -> Unit, onSeekFinished: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatDuration(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
            onValueChangeFinished = onSeekFinished,
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = INACTIVE_TRACK_ALPHA),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.m),
        )
        Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
