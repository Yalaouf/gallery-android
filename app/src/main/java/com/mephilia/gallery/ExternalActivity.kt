package com.mephilia.gallery

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaRepository
import com.mephilia.gallery.ui.Route
import com.mephilia.gallery.ui.editRouteFor
import com.mephilia.gallery.ui.editor.GifEditorScreen
import com.mephilia.gallery.ui.editor.PhotoEditorScreen
import com.mephilia.gallery.ui.editor.VideoEditorScreen
import com.mephilia.gallery.ui.screens.ViewerScreen
import com.mephilia.gallery.ui.theme.GalleryTheme

/**
 * Entry point for other apps: "Edit with" (ACTION_EDIT), "Open with" (ACTION_VIEW), and
 * "Edit with Gallery" in the share menu (ACTION_SEND). Sharing is the only way in from a
 * screenshot, whose Edit button always launches the system editor.
 * Closing the editor returns to the calling app.
 */
class ExternalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        val shared = intent.action == Intent.ACTION_SEND
        val uri = if (shared) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.data
        if (uri == null) {
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

@Composable
private fun ExternalRoot(uri: Uri, type: String?, editMode: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val item by produceState<MediaItem?>(null, uri) {
        value = MediaRepository(context).resolve(uri, type)
        if (value == null) onFinish()
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
