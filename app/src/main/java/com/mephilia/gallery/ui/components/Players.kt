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

/** Lecteur avec repli sur un autre décodeur si le décodeur matériel échoue. */
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
 * Coupe complètement la piste audio et, dans ce cas, ne demande pas le focus audio :
 * les autres applications (YouTube en écran partagé…) continuent de jouer.
 * Avec le son, le focus est demandé normalement et les autres apps se mettent en pause.
 */
fun ExoPlayer.setMuted(muted: Boolean) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, muted)
        .build()
    setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
        /* handleAudioFocus = */ !muted,
    )
}

/** Vrai en écran partagé / fenêtre flottante. Réévalué à chaque changement de configuration. */
@Composable
fun isInMultiWindow(): Boolean {
    LocalConfiguration.current
    return (LocalContext.current as? Activity)?.isInMultiWindowMode == true
}
