package com.mephilia.gallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mephilia.gallery.R
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.FullCrop
import com.mephilia.gallery.ui.components.aspectPresets
import com.mephilia.gallery.ui.components.centeredCrop
import com.mephilia.gallery.ui.components.createPlayer
import com.mephilia.gallery.ui.components.formatDuration
import com.mephilia.gallery.ui.components.isInMultiWindow
import com.mephilia.gallery.ui.components.setMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Number of frames shown in the trim bar. */
private const val TRIM_BAR_FRAMES = 10

/** Height of the frames extracted for the trim bar, in pixels. */
private const val TRIM_BAR_FRAME_HEIGHT_PX = 120

/** How often the playback position is checked, to update the trim bar and loop within the trimmed part. */
private const val POSITION_POLL_MS = 40L

/** Aspect ratio assumed until the video's real size is known. */
private const val DEFAULT_VIDEO_RATIO = 16f / 9f

/** MIME type of a saved video. */
private const val VIDEO_MIME = "video/mp4"

/** File extension of a saved video. */
private const val VIDEO_EXTENSION = "mp4"

/** The tools of the video editor, one shown at a time above the tool bar. */
internal enum class VideoTool { Trim, Crop, Rotate, Speed }

/**
 * Editor for videos: trim (with a live preview), crop, turn and mirror, change the speed, remove the sound.
 * The top bar also saves the current frame as a photo, and exports the trimmed part as a GIF.
 * Playback loops within the trimmed part.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by produceState<VideoInfo?>(null, item.uri) {
        value = runCatching { readVideoInfo(context, item.uri) }.getOrNull()
    }
    val frames by produceState(emptyList<Bitmap>(), info) {
        val duration = info?.durationMs ?: return@produceState
        value = runCatching {
            extractFrames(context, item.uri, duration, count = TRIM_BAR_FRAMES, height = TRIM_BAR_FRAME_HEIGHT_PX)
        }.getOrDefault(emptyList())
    }

    val multiWindow = isInMultiWindow()
    var muted by remember { mutableStateOf(multiWindow || AppSettings.current.alwaysMuted) }
    val player = remember { createPlayer(context, item.uri, muted) }
    val isPlaying = rememberIsPlaying(player)
    LaunchedEffect(multiWindow) { if (multiWindow) muted = true }
    LaunchedEffect(muted) { player.setMuted(muted) }
    DisposableEffect(player) { onDispose { player.release() } }

    var tool by remember { mutableStateOf(VideoTool.Trim) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var geometry by remember { mutableStateOf(PhotoEdits()) }
    var speed by remember { mutableFloatStateOf(NORMAL_SPEED) }
    var removeAudio by remember { mutableStateOf(false) }
    var presetIndex by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var exportingGif by remember { mutableStateOf(false) }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(info) { info?.let { endMs = it.durationMs } }

    val range by rememberUpdatedState(startMs to endMs)
    LaunchedEffect(player) {
        while (true) {
            delay(POSITION_POLL_MS)
            position = player.currentPosition
            val (start, end) = range
            if (end > 0 && player.isPlaying && position >= end) player.seekTo(start)
        }
    }

    val ratio = turnedRatio(info?.ratio ?: DEFAULT_VIDEO_RATIO, geometry.quarterTurns)
    val freeLabel = stringResource(R.string.aspect_free)
    val originalLabel = stringResource(R.string.aspect_original)
    val presets = remember(ratio, freeLabel) { aspectPresets(ratio, freeLabel, originalLabel) }
    val duration = info?.durationMs ?: 0L
    val trimmed = startMs > 0 || endMs in 1 until duration
    val cropped = geometry.crop != FullCrop
    val retimed = speed != NORMAL_SPEED || removeAudio
    val edited = trimmed || cropped || geometry.isTurnedOrMirrored || retimed
    val ready = info != null && !saving

    EditorScaffold(
        title = stringResource(R.string.edit_video_title),
        isVideo = true,
        onClose = onClose,
        onSave = {
            player.pause()
            saving = true
        },
        saveEnabled = ready && edited,
        actions = {
            IconButton(
                enabled = ready,
                onClick = {
                    player.pause()
                    val at = player.currentPosition
                    scope.launch { saveFrame(context, item, at, geometry) }
                },
            ) { Icon(Icons.Rounded.PhotoCamera, stringResource(R.string.save_frame), tint = Color.White) }
            IconButton(
                enabled = ready,
                onClick = {
                    player.pause()
                    if (checkGifLength(context, endMs - startMs)) exportingGif = true
                },
            ) { Icon(Icons.Rounded.GifBox, stringResource(R.string.gif_button), tint = Color.White) }
        },
        preview = {
            VideoPreview(
                loaded = info != null,
                player = player,
                ratio = ratio,
                geometry = geometry,
                showCropTool = tool == VideoTool.Crop,
                cropRatio = presets[presetIndex].ratio,
                scrubMs = scrubMs,
                onCropChange = { geometry = geometry.copy(crop = it) },
                onTogglePlay = { togglePlay(player, startMs, endMs) },
            )
        },
        tools = {
            when (tool) {
                VideoTool.Trim -> TrimPanel(
                    durationMs = duration,
                    startMs = startMs,
                    endMs = endMs,
                    positionMs = position,
                    frames = frames,
                    isPlaying = isPlaying,
                    muted = muted,
                    onTogglePlay = { togglePlay(player, startMs, endMs) },
                    onToggleMute = { muted = !muted },
                    onScrubStart = { movedStart ->
                        player.pause()
                        player.setScrubbingModeEnabled(true)
                        scrubMs = (if (movedStart) startMs else endMs).also(player::seekTo)
                    },
                    onRangeChange = { start, end, movedStart ->
                        startMs = start
                        endMs = end
                        scrubMs = (if (movedStart) start else end).also(player::seekTo)
                    },
                    onScrubEnd = {
                        player.setScrubbingModeEnabled(false)
                        scrubMs = null
                    },
                )
                VideoTool.Crop -> AspectChips(presets, presets[presetIndex]) {
                    presetIndex = presets.indexOf(it)
                    geometry = geometry.copy(crop = centeredCrop(it.ratio, ratio))
                }
                VideoTool.Speed -> SpeedPanel(
                    speed = speed,
                    removeAudio = removeAudio,
                    onSpeedChange = { speed = it },
                    onRemoveAudioChange = { removeAudio = it },
                )
                VideoTool.Rotate -> TurnPanel(
                    mirrored = geometry.flipH,
                    onRotate = {
                        geometry = geometry.rotated(it)
                        presetIndex = 0
                    },
                    onMirror = { geometry = geometry.mirrored() },
                )
            }
            VideoToolBar(
                tool = tool,
                trimmed = trimmed,
                cropped = cropped,
                turned = geometry.isTurnedOrMirrored,
                retimed = retimed,
                onSelect = {
                    if (it == VideoTool.Crop) player.pause()
                    tool = it
                },
                onReset = {
                    speed = NORMAL_SPEED
                    removeAudio = false
                    startMs = 0
                    endMs = duration
                    geometry = PhotoEdits()
                    presetIndex = 0
                },
            )
        },
    )

    val loadedInfo = info
    if (exportingGif && loadedInfo != null) {
        GifExportFlow(
            item = item,
            info = loadedInfo,
            startMs = startMs,
            endMs = endMs,
            edits = geometry,
            onDismiss = { exportingGif = false },
        )
    }

    SaveFlow(
        item = item,
        active = saving,
        onCancel = { saving = false },
        outputMime = VIDEO_MIME,
        outputExtension = VIDEO_EXTENSION,
        produce = { onProgress ->
            exportVideo(
                context, item.uri, startMs, endMs, duration, geometry.quarterTurns, geometry.flipH, geometry.crop,
                MediaWriter(context).tempFile(VIDEO_EXTENSION), onProgress, speed = speed, removeAudio = removeAudio,
            )
        },
        onSaved = {
            saving = false
            onClose()
        },
    )
}

/** Whether [player] is currently playing, updated as it starts and stops. */
@Composable
private fun rememberIsPlaying(player: ExoPlayer): Boolean {
    var isPlaying by remember(player) { mutableStateOf(false) }
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

/** Saves the frame at [positionMs] as a photo, with the video's [geometry], and says whether it worked. */
private suspend fun saveFrame(context: Context, item: MediaItem, positionMs: Long, geometry: PhotoEdits) {
    val saved = runCatching { saveVideoFrame(context, item, positionMs, geometry) }.isSuccess
    Toast.makeText(context, context.getString(if (saved) R.string.frame_saved else R.string.frame_failed), Toast.LENGTH_SHORT).show()
}

/**
 * Whether a part of [selectionMs] can be exported as a GIF. If it's too long,
 * a message gives the limit and the current length, and this returns false.
 */
private fun checkGifLength(context: Context, selectionMs: Long): Boolean {
    if (selectionMs <= GIF_MAX_DURATION_MS) return true
    Toast.makeText(
        context,
        context.getString(R.string.gif_too_long, formatDuration(GIF_MAX_DURATION_MS), formatDuration(selectionMs)),
        Toast.LENGTH_LONG,
    ).show()
    return false
}

/**
 * Pauses, or plays from where the player is. If that's outside the part between [startMs] and [endMs],
 * playback starts from [startMs].
 */
internal fun togglePlay(player: Player, startMs: Long, endMs: Long) {
    if (player.isPlaying) {
        player.pause()
    } else {
        if (player.currentPosition !in startMs until endMs) player.seekTo(startMs)
        player.play()
    }
}
