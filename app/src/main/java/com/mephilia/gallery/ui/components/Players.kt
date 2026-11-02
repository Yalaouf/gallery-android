package com.mephilia.gallery.ui.components

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

/**
 * A player for the video at [uri], ready to play, [muted] or not. It falls back to another decoder if the
 * hardware one fails to initialize.
 */
@OptIn(UnstableApi::class)
fun createPlayer(context: Context, uri: Uri, muted: Boolean): ExoPlayer =
    ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .build()
        .apply {
            setMuted(muted)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }

/**
 * When muted, disables the audio track entirely and doesn't request audio focus, so other apps
 * (e.g. YouTube in split screen) keep playing. Unmuted, focus is requested and other apps pause.
 */
fun ExoPlayer.setMuted(muted: Boolean) {
    val handleAudioFocus = !muted
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, muted)
        .build()
    setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
        handleAudioFocus,
    )
}

/**
 * True in split screen or a floating window. Reading the configuration makes it re-evaluated on every
 * configuration change.
 */
@Composable
fun isInMultiWindow(): Boolean {
    LocalConfiguration.current
    return (LocalContext.current as? Activity)?.isInMultiWindowMode == true
}
