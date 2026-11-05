package com.mephilia.gallery

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaRepository
import com.mephilia.gallery.ui.Route
import com.mephilia.gallery.ui.editRouteFor
import com.mephilia.gallery.ui.editor.GifEditorScreen
import com.mephilia.gallery.ui.editor.PhotoEditorScreen
import com.mephilia.gallery.ui.editor.VideoEditorScreen
import com.mephilia.gallery.ui.screens.viewer.ViewerScreen
import com.mephilia.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.flow.filter

/**
 * Entry point for other apps: "Edit with" (ACTION_EDIT), "Open with" (ACTION_VIEW), and
 * "Edit with Gallery" in the share menu (ACTION_SEND). Sharing is the only way in from a
 * screenshot, whose Edit button always launches the system editor.
 * Closing the editor returns to the calling app.
 *
 * Only content URIs are accepted, which is what apps share: a file URI could point into this app's private
 * storage, and saving or sharing it would leak those files.
 */
class ExternalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        val shared = intent.action == Intent.ACTION_SEND
        val uri = if (shared) IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else intent.data
        if (uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT) {
            finish()
            return
        }
        val editMode = shared || intent.action == Intent.ACTION_EDIT
        val type = intent.type
        setContent {
            GalleryTheme {
                ExternalRoot(uri, type, editMode, onFinish = ::finish)
            }
        }
    }
}

/**
 * The file at [uri], in the editor if [editMode], otherwise in the viewer (whose edit button opens the editor).
 * Closes the activity ([onFinish]) if the file can't be read, or when the editor opened directly is closed.
 * The file is read again when it changes (renamed, date edited…), so the viewer shows its new details.
 *
 * @param type the MIME type given by the calling app, if any.
 */
@Composable
private fun ExternalRoot(uri: Uri, type: String?, editMode: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val item by produceState<MediaItem?>(null, uri) {
        val repository = MediaRepository(context)
        value = repository.resolve(uri, type)
        if (value == null) onFinish()
        MediaEvents.events
            .filter { it is MediaEvents.Event.Changed || it is MediaEvents.Event.Replaced }
            .collect { repository.resolve(uri, type)?.let { value = it } }
    }
    val media = item ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    var editing by rememberSaveable { mutableStateOf(editMode) }

    if (editing) {
        val close = { if (editMode) onFinish() else editing = false }
        BackHandler(onBack = close)
        when (editRouteFor(media)) {
            is Route.EditVideo -> VideoEditorScreen(item = media, onClose = close)
            is Route.EditGif -> GifEditorScreen(item = media, onClose = close)
            else -> PhotoEditorScreen(item = media, onClose = close)
        }
    } else {
        ViewerScreen(
            vm = viewModel(),
            bucketId = null,
            startId = media.id,
            onBack = onFinish,
            onEdit = { editing = true },
            items = listOf(media),
        )
    }
}
