package com.mephilia.gallery.ui.editor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Rect
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.components.FullCrop
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How often the export progress is checked. */
private const val PROGRESS_POLL_MS = 200L

/** Media3 reports progress as a percentage. */
private const val PERCENT = 100f

/**
 * Exports the part of the video at [uri] between [startMs] and [endMs] to MP4 with Media3 Transformer.
 *
 * [quarterTurns] (clockwise) and [flipH] are applied before [crop], which is normalized (0 to 1) in the turned
 * picture. A [speed] other than 1 is constant over the video, and the sound keeps its pitch.
 *
 * @param durationMs the whole video's duration, to know whether the end is trimmed.
 * @param onProgress the share done, from 0 to 1.
 * @throws Exception with a readable message if the export fails.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
suspend fun exportVideo(
    context: Context,
    uri: Uri,
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    quarterTurns: Int,
    flipH: Boolean,
    crop: Rect,
    out: File,
    onProgress: (Float) -> Unit,
    speed: Float = NORMAL_SPEED,
    removeAudio: Boolean = false,
): File = withContext(Dispatchers.Main) {
    val clipping = MediaItem.ClippingConfiguration.Builder().apply {
        if (startMs > 0) setStartPositionMs(startMs)
        if (endMs < durationMs) setEndPositionMs(endMs)
    }.build()
    val mediaItem = MediaItem.Builder().setUri(uri).setClippingConfiguration(clipping).build()
    val videoEffects = videoEffects(quarterTurns, flipH, crop)
    val edited = EditedMediaItem.Builder(mediaItem)
        .setEffects(if (videoEffects.isEmpty()) Effects.EMPTY else Effects(emptyList(), videoEffects))
        .apply {
            if (speed != NORMAL_SPEED) setSpeed(SpeedParameters(constantSpeed(speed), true))
            if (removeAudio) setRemoveAudio(true)
        }
        .build()

    suspendCancellableCoroutine { continuation -> runTransformer(context, edited, out, onProgress, continuation) }
}

/**
 * The video effects for the turns, mirror and crop. Media3 rotates counterclockwise, hence the negative angle,
 * and its crop takes OpenGL coordinates (see [glCrop]).
 */
@OptIn(UnstableApi::class)
private fun videoEffects(quarterTurns: Int, flipH: Boolean, crop: Rect): List<Effect> = buildList {
    val turns = quarterTurns.mod(QUARTER_TURNS_PER_TURN)
    if (turns != 0) add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-DEGREES_PER_QUARTER_TURN * turns).build())
    if (flipH) add(ScaleAndRotateTransformation.Builder().setScale(-1f, 1f).build())
    if (crop != FullCrop) glCrop(crop).let { (left, right, bottom, top) -> add(Crop(left, right, bottom, top)) }
}

/** A speed that stays the same over the whole video. */
@OptIn(UnstableApi::class)
private fun constantSpeed(speed: Float) = object : SpeedProvider {
    override fun getSpeed(timeUs: Long) = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
}

/**
 * Runs the export of [edited] to [out] and resumes [continuation] with the file when done, or with an error.
 * The progress is polled on the main thread while it runs. Cancelling stops the export and deletes the file.
 */
@OptIn(UnstableApi::class)
private fun runTransformer(
    context: Context,
    edited: EditedMediaItem,
    out: File,
    onProgress: (Float) -> Unit,
    continuation: CancellableContinuation<File>,
) {
    val handler = Handler(Looper.getMainLooper())
    val transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (continuation.isActive) continuation.resume(out)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                if (continuation.isActive) {
                    val message = context.getString(R.string.error_video_export, exportException.errorCodeName)
                    continuation.resumeWithException(Exception(message, exportException))
                }
            }
        })
        .build()
    transformer.start(edited, out.absolutePath)

    val holder = ProgressHolder()
    val poll = object : Runnable {
        override fun run() {
            if (!continuation.isActive) return
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / PERCENT)
            handler.postDelayed(this, PROGRESS_POLL_MS)
        }
    }
    handler.post(poll)
    continuation.invokeOnCancellation {
        handler.post {
            handler.removeCallbacks(poll)
            transformer.cancel()
            out.delete()
        }
    }
}

/**
 * Converts a normalized crop (0 to 1, y pointing down) to OpenGL coordinates as Media3's Crop expects them:
 * left, right, bottom, top, from −1 to 1, with y pointing up.
 */
internal fun glCrop(crop: Rect): List<Float> =
    listOf(2 * crop.left - 1, 2 * crop.right - 1, 1 - 2 * crop.bottom, 1 - 2 * crop.top)
