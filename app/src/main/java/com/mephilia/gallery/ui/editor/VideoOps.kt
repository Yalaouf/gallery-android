package com.mephilia.gallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Rect
import com.mephilia.gallery.R
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.mephilia.gallery.ui.components.FullCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class VideoInfo(val width: Int, val height: Int, val durationMs: Long) {
    val ratio get() = if (height > 0) width.toFloat() / height else 16f / 9f
}

/** Dimensions telles qu'affichées (rotation appliquée) et durée. */
suspend fun readVideoInfo(context: Context, uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
    MediaMetadataRetriever().use { r ->
        r.setDataSource(context, uri)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        if (rot % 180 != 0) VideoInfo(h, w, d) else VideoInfo(w, h, d)
    }
}

/** Images régulièrement espacées pour la frise de découpe. */
suspend fun extractFrames(context: Context, uri: Uri, durationMs: Long, count: Int, height: Int): List<Bitmap> =
    withContext(Dispatchers.IO) {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            (0 until count).mapNotNull { i ->
                val t = durationMs * 1000 * (2 * i + 1) / (2 * count)
                r.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, height * 2, height)
            }
        }
    }

/**
 * Exporte la vidéo découpée/recadrée en MP4 avec Media3 Transformer.
 * [quarterTurns] (sens horaire) et [flipH] sont appliqués avant [crop], normalisé (0..1) dans l'espace affiché.
 */
@OptIn(UnstableApi::class)
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
): File = withContext(Dispatchers.Main) {
    val clipping = MediaItem.ClippingConfiguration.Builder().apply {
        if (startMs > 0) setStartPositionMs(startMs)
        if (endMs < durationMs) setEndPositionMs(endMs)
    }.build()
    val mediaItem = MediaItem.Builder().setUri(uri).setClippingConfiguration(clipping).build()
    // Même ordre que pour les photos : rotation, puis miroir, puis recadrage (dans l'espace affiché)
    val videoEffects = buildList<Effect> {
        val turns = quarterTurns.mod(4)
        // Media3 tourne dans le sens antihoraire
        if (turns != 0) add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-90f * turns).build())
        if (flipH) add(ScaleAndRotateTransformation.Builder().setScale(-1f, 1f).build())
        // Coordonnées normalisées OpenGL : x et y dans [-1, 1], y vers le haut
        if (crop != FullCrop) add(Crop(2 * crop.left - 1, 2 * crop.right - 1, 1 - 2 * crop.bottom, 1 - 2 * crop.top))
    }
    val effects = if (videoEffects.isEmpty()) Effects.EMPTY else Effects(emptyList(), videoEffects)
    val edited = EditedMediaItem.Builder(mediaItem).setEffects(effects).build()

    suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(out)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (cont.isActive) cont.resumeWithException(
                        Exception(context.getString(R.string.error_video_export, exportException.errorCodeName), exportException),
                    )
                }
            })
            .build()
        transformer.start(edited, out.absolutePath)

        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                if (!cont.isActive) return
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(poll)
        cont.invokeOnCancellation {
            handler.post {
                handler.removeCallbacks(poll)
                transformer.cancel()
                out.delete()
            }
        }
    }
}
