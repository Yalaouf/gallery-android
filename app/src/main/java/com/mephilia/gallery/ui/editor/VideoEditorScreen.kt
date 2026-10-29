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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.CropOverlay
import com.mephilia.gallery.ui.components.FullCrop
import com.mephilia.gallery.ui.components.aspectPresets
import com.mephilia.gallery.ui.components.centeredCrop
import com.mephilia.gallery.ui.components.createPlayer
import com.mephilia.gallery.ui.components.isInMultiWindow
import com.mephilia.gallery.ui.components.setMuted
import com.mephilia.gallery.ui.components.formatDuration
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R

private enum class VideoTool { Trim, Crop, Rotate }

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val info by produceState<VideoInfo?>(null, item.uri) {
        value = runCatching { readVideoInfo(context, item.uri) }.getOrNull()
    }
    val frames by produceState(emptyList<Bitmap>(), info) {
        val d = info?.durationMs ?: return@produceState
        value = runCatching { extractFrames(context, item.uri, d, count = 10, height = 120) }.getOrDefault(emptyList())
    }

    val multiWindow = isInMultiWindow()
    var muted by remember { mutableStateOf(multiWindow || AppSettings.current.alwaysMuted) }
    LaunchedEffect(multiWindow) { if (multiWindow) muted = true }
    val player = remember { createPlayer(context, item.uri, muted) }
    LaunchedEffect(muted) { player.setMuted(muted) }
    DisposableEffect(player) { onDispose { player.release() } }
    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    var tool by remember { mutableStateOf(VideoTool.Trim) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var crop by remember { mutableStateOf(FullCrop) }
    /** Quarts de tour dans le sens horaire, et miroir horizontal appliqué après la rotation. */
    var quarterTurns by remember { mutableIntStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    /** Position de la poignée en cours de déplacement, null hors glissement. */
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var presetIndex by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(info) { info?.let { endMs = it.durationMs } }

    // Lecture en boucle à l'intérieur de la sélection
    val range by rememberUpdatedState(startMs to endMs)
    LaunchedEffect(player) {
        while (true) {
            delay(40)
            position = player.currentPosition
            val (s, e) = range
            if (e > 0 && player.isPlaying && position >= e) player.seekTo(s)
        }
    }

    val sourceRatio = info?.ratio ?: 16f / 9f
    // Ratio affiché, une fois la rotation appliquée
    val ratio = if (quarterTurns.mod(2) == 1) 1f / sourceRatio else sourceRatio
    val freeLabel = stringResource(R.string.aspect_free)
    val originalLabel = stringResource(R.string.aspect_original)
    val presets = remember(ratio, freeLabel) { aspectPresets(ratio, freeLabel, originalLabel) }
    val duration = info?.durationMs ?: 0L
    val edited = startMs > 0 || (endMs in 1 until duration) || crop != FullCrop ||
        quarterTurns.mod(4) != 0 || flipH

    fun rotate(delta: Int) {
        quarterTurns += delta
        crop = FullCrop
        presetIndex = 0
    }

    EditorScaffold(
        title = stringResource(R.string.edit_video_title),
        isVideo = true,
        onClose = onClose,
        onSave = {
            player.pause()
            saving = true
        },
        saveEnabled = info != null && edited && !saving,
        preview = {
            if (info == null) {
                CircularProgressIndicator()
            } else {
                FitBox(ratio) {
                    // TextureView (contrairement à SurfaceView) peut être pivotée et retournée à l'affichage
                    Transformed(quarterTurns, flipH) {
                        AndroidView(
                            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
                            onRelease = { player.clearVideoTextureView(it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    scrubMs?.let { ms ->
                        Text(
                            formatPreciseTime(ms),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                    if (tool == VideoTool.Crop) {
                        CropOverlay(
                            crop = crop,
                            onCropChange = { crop = it },
                            ratio = presets[presetIndex].ratio,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { togglePlay(player, startMs, endMs) },
                        )
                    }
                }
            }
        },
        tools = {
            if (tool == VideoTool.Trim) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            .clickable { togglePlay(player, startMs, endMs) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(R.string.play), tint = Color.White,
                        )
                    }
                    Text(
                        "${formatDuration(startMs)} – ${formatDuration(endMs)}",
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp).weight(1f),
                    )
                    Text(
                        formatDuration(endMs - startMs),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    IconButton(onClick = { muted = !muted }) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            stringResource(if (muted) R.string.sound_on else R.string.sound_off),
                            tint = Color.White,
                        )
                    }
                }
                TrimBar(
                    durationMs = duration,
                    startMs = startMs,
                    endMs = endMs,
                    positionMs = position,
                    frames = frames,
                    onChangeStarted = { movedStart ->
                        // Mode "scrubbing" : Media3 privilégie l'affichage rapide des images pendant les seeks répétés
                        player.pause()
                        player.setScrubbingModeEnabled(true)
                        scrubMs = if (movedStart) startMs else endMs
                        player.seekTo(scrubMs!!)
                    },
                    onChange = { s, e, movedStart ->
                        startMs = s
                        endMs = e
                        scrubMs = if (movedStart) s else e
                        player.seekTo(scrubMs!!)
                    },
                    onChangeFinished = {
                        // On reste sur l'image de la poignée relâchée pour pouvoir la vérifier
                        player.setScrubbingModeEnabled(false)
                        scrubMs = null
                    },
                )
            } else if (tool == VideoTool.Crop) {
                AspectChips(presets, presets[presetIndex]) {
                    presetIndex = presets.indexOf(it)
                    crop = centeredCrop(it.ratio, ratio)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ToolButton(Icons.AutoMirrored.Rounded.RotateLeft, stringResource(R.string.tool_left)) { rotate(-1) }
                    Spacer(Modifier.width(24.dp))
                    ToolButton(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.tool_right)) { rotate(1) }
                    Spacer(Modifier.width(24.dp))
                    ToolButton(Icons.Rounded.Flip, stringResource(R.string.tool_mirror), selected = flipH) {
                        flipH = !flipH
                        crop = crop.let { c -> c.copy(left = 1f - c.right, right = 1f - c.left) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.Rounded.ContentCut, stringResource(R.string.tool_trim), selected = tool == VideoTool.Trim) { tool = VideoTool.Trim }
                ToolButton(Icons.Rounded.Crop, stringResource(R.string.tool_crop), selected = tool == VideoTool.Crop) {
                    player.pause()
                    tool = VideoTool.Crop
                }
                ToolButton(Icons.Rounded.Rotate90DegreesCw, stringResource(R.string.tool_rotate), selected = tool == VideoTool.Rotate) {
                    tool = VideoTool.Rotate
                }
                ToolButton(Icons.Rounded.RestartAlt, stringResource(R.string.tool_reset)) {
                    startMs = 0
                    endMs = duration
                    crop = FullCrop
                    presetIndex = 0
                    quarterTurns = 0
                    flipH = false
                }
            }
        },
    )

    SaveFlow(
        item = item,
        active = saving,
        onCancel = { saving = false },
        outputMime = "video/mp4",
        outputExtension = "mp4",
        produce = { onProgress ->
            exportVideo(
                context, item.uri, startMs, endMs, duration, quarterTurns, flipH, crop,
                MediaWriter(context).tempFile("mp4"), onProgress,
            )
        },
        onSaved = {
            saving = false
            onClose()
        },
    )
}

/**
 * Affiche [content] pivoté de [quarterTurns] quarts de tour (sens horaire) puis retourné horizontalement.
 * Le contenu est mesuré aux dimensions d'origine (largeur/hauteur échangées si besoin).
 */
@Composable
private fun Transformed(quarterTurns: Int, flipH: Boolean, content: @Composable () -> Unit) {
    val turns = quarterTurns.mod(4)
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = if (flipH) -1f else 1f }) {
        Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val childConstraints = if (turns % 2 == 1) Constraints.fixed(h, w) else Constraints.fixed(w, h)
            val placeable = measurables.first().measure(childConstraints)
            layout(w, h) {
                placeable.placeWithLayer((w - placeable.width) / 2, (h - placeable.height) / 2) {
                    rotationZ = 90f * turns
                }
            }
        }
    }
}

/** Temps au dixième de seconde (ex. 1:04.3), pour un positionnement précis. */
private fun formatPreciseTime(ms: Long) = "%s.%d".format(formatDuration(ms), (ms % 1000) / 100)

private fun togglePlay(player: Player, startMs: Long, endMs: Long) {
    if (player.isPlaying) {
        player.pause()
    } else {
        if (player.currentPosition !in startMs until endMs) player.seekTo(startMs)
        player.play()
    }
}
