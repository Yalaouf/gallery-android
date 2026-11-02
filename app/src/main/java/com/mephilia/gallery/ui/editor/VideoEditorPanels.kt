package com.mephilia.gallery.ui.editor

import android.graphics.Bitmap
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.components.CropOverlay
import com.mephilia.gallery.ui.components.FullCrop
import com.mephilia.gallery.ui.components.formatDuration
import com.mephilia.gallery.ui.theme.Spacing

/** Playback speeds offered for a video. */
private val VIDEO_SPEEDS = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f)

/** Horizontal padding of the trim and speed panels' rows. */
private val PANEL_HORIZONTAL_PADDING = 20.dp

/** Size of the round play button of the trim panel. */
private val PLAY_BUTTON_SIZE = 44.dp

/** Opacity of the play button's light background. */
private const val PLAY_BUTTON_BACKGROUND_ALPHA = 0.12f

/** Opacity of the dark background of the time shown while scrubbing. */
private const val SCRUB_TIME_BACKGROUND_ALPHA = 0.6f

/** Horizontal padding inside the time shown while scrubbing. */
private val SCRUB_TIME_HORIZONTAL_PADDING = 14.dp

/** Vertical padding inside the time shown while scrubbing. */
private val SCRUB_TIME_VERTICAL_PADDING = 6.dp

/** Milliseconds in a tenth of a second, the precision of the time shown while scrubbing. */
private const val MILLIS_PER_TENTH = 100L

/** Milliseconds in a second. */
private const val MILLIS_PER_SECOND = 1000L

/**
 * The video being edited. Outside the crop tool it shows the cropped result; in the crop tool it shows the whole
 * frame with the crop overlay, constrained to [cropRatio] if set. While a trim handle is dragged, the time of the
 * frame shown ([scrubMs]) is displayed on top. Tapping the video plays or pauses it.
 *
 * @param loaded whether the video's size and duration are known; until then a spinner is shown.
 * @param ratio aspect ratio of the turned video.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPreview(
    loaded: Boolean,
    player: ExoPlayer,
    ratio: Float,
    geometry: PhotoEdits,
    showCropTool: Boolean,
    cropRatio: Float?,
    scrubMs: Long?,
    onCropChange: (Rect) -> Unit,
    onTogglePlay: () -> Unit,
) {
    if (!loaded) {
        CircularProgressIndicator()
        return
    }
    val crop = geometry.crop
    FitBox(if (showCropTool) ratio else ratio * crop.width / crop.height) {
        CroppedTo(if (showCropTool) FullCrop else crop) {
            Transformed(geometry.quarterTurns, geometry.flipH) { VideoSurface(player) }
        }
        scrubMs?.let { ScrubTime(it, Modifier.align(Alignment.TopCenter)) }
        if (showCropTool) {
            CropOverlay(crop = crop, onCropChange = onCropChange, ratio = cropRatio, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTogglePlay),
            )
        }
    }
}

/**
 * Where [player] draws the video. A TextureView rather than a SurfaceView, because it can be turned and mirrored
 * when drawn.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: ExoPlayer) {
    AndroidView(
        factory = { context -> TextureView(context).also { player.setVideoTextureView(it) } },
        onRelease = { player.clearVideoTextureView(it) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** The time of the frame shown while scrubbing, to the tenth of a second, e.g. "0:12.4". */
@Composable
private fun ScrubTime(ms: Long, modifier: Modifier = Modifier) {
    Text(
        "%s.%d".format(formatDuration(ms), (ms % MILLIS_PER_SECOND) / MILLIS_PER_TENTH),
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .padding(top = Spacing.m)
            .background(Color.Black.copy(alpha = SCRUB_TIME_BACKGROUND_ALPHA), CircleShape)
            .padding(horizontal = SCRUB_TIME_HORIZONTAL_PADDING, vertical = SCRUB_TIME_VERTICAL_PADDING),
    )
}

/**
 * The trim tool: play / pause, the trimmed range and its length, sound on / off, then the [TrimBar].
 *
 * Moving a handle seeks to the frame under it. Scrubbing mode (set up by [onScrubStart]) makes the player favor
 * fast frame display during repeated seeks; once released, the player stays on the handle's frame so it can be
 * checked.
 *
 * @param onScrubStart a handle starts moving; true for the start handle, false for the end one.
 * @param onRangeChange the range changed to start, end; the boolean tells which handle moved, as above.
 */
@Composable
internal fun TrimPanel(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    positionMs: Long,
    frames: List<Bitmap>,
    isPlaying: Boolean,
    muted: Boolean,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onScrubStart: (Boolean) -> Unit,
    onRangeChange: (Long, Long, Boolean) -> Unit,
    onScrubEnd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PANEL_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(PLAY_BUTTON_SIZE)
                .background(Color.White.copy(alpha = PLAY_BUTTON_BACKGROUND_ALPHA), CircleShape)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (isPlaying) R.string.pause else R.string.play),
                tint = Color.White,
            )
        }
        Text(
            "${formatDuration(startMs)} – ${formatDuration(endMs)}",
            color = Color.White,
            modifier = Modifier.padding(start = Spacing.l).weight(1f),
        )
        Text(formatDuration(endMs - startMs), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onToggleMute) {
            Icon(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                stringResource(if (muted) R.string.sound_on else R.string.sound_off),
                tint = Color.White,
            )
        }
    }
    TrimBar(
        durationMs = durationMs,
        startMs = startMs,
        endMs = endMs,
        positionMs = positionMs,
        frames = frames,
        onChangeStarted = onScrubStart,
        onChange = onRangeChange,
        onChangeFinished = onScrubEnd,
    )
}

/** The speed tool: the speed chips, and a switch to remove the sound. */
@Composable
internal fun SpeedPanel(
    speed: Float,
    removeAudio: Boolean,
    onSpeedChange: (Float) -> Unit,
    onRemoveAudioChange: (Boolean) -> Unit,
) {
    ChipRow { speedChips(VIDEO_SPEEDS, speed, onSpeedChange) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onRemoveAudioChange(!removeAudio) }
            .padding(horizontal = PANEL_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.remove_sound), color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = removeAudio, onCheckedChange = onRemoveAudioChange)
    }
}

/** The rotate tool: turn left, turn right and mirror, centered. */
@Composable
internal fun TurnPanel(mirrored: Boolean, onRotate: (Int) -> Unit, onMirror: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterHorizontally)) {
        TurnButtons(mirrored, onRotate, onMirror)
    }
}

/**
 * The video editor's tool bar: one button per tool, with a dot on those that have a pending edit,
 * and a button to [onReset] every edit.
 */
@Composable
internal fun VideoToolBar(
    tool: VideoTool,
    trimmed: Boolean,
    cropped: Boolean,
    turned: Boolean,
    retimed: Boolean,
    onSelect: (VideoTool) -> Unit,
    onReset: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ToolButton(Icons.Rounded.ContentCut, stringResource(R.string.tool_trim), tool == VideoTool.Trim, trimmed) {
            onSelect(VideoTool.Trim)
        }
        ToolButton(Icons.Rounded.Crop, stringResource(R.string.tool_crop), tool == VideoTool.Crop, cropped) {
            onSelect(VideoTool.Crop)
        }
        ToolButton(Icons.Rounded.Rotate90DegreesCw, stringResource(R.string.tool_rotate), tool == VideoTool.Rotate, turned) {
            onSelect(VideoTool.Rotate)
        }
        ToolButton(Icons.Rounded.Speed, stringResource(R.string.tool_speed), tool == VideoTool.Speed, retimed) {
            onSelect(VideoTool.Speed)
        }
        ToolButton(Icons.Rounded.RestartAlt, stringResource(R.string.tool_reset), onClick = onReset)
    }
}
