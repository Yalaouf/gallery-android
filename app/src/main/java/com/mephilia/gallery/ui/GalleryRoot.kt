package com.mephilia.gallery.ui

import com.mephilia.gallery.ui.screens.NewAlbumScreen
import com.mephilia.gallery.ui.screens.SecureWindow
import com.mephilia.gallery.ui.screens.LockedScreen
import com.mephilia.gallery.data.LOCKED_ID
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.editor.PhotoEditorScreen
import com.mephilia.gallery.ui.editor.VideoEditorScreen
import com.mephilia.gallery.ui.screens.AlbumScreen
import com.mephilia.gallery.ui.screens.HomeScreen
import com.mephilia.gallery.ui.screens.ViewerScreen
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.editor.GifEditorScreen
import com.mephilia.gallery.ui.screens.TrashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

sealed interface Route {
    val key: String

    data object Home : Route {
        override val key = "home"
    }

    data class Album(val id: Long, val name: String) : Route {
        override val key = "album/$id"
    }

    data class Viewer(val bucketId: Long?, val startId: Long) : Route {
        override val key = "viewer/$bucketId/$startId"
    }

    data class EditPhoto(val item: MediaItem) : Route {
        override val key = "edit-photo/${item.id}"
    }

    data class EditVideo(val item: MediaItem) : Route {
        override val key = "edit-video/${item.id}"
    }

    data object Trash : Route {
        override val key = "trash"
    }

    data class TrashViewer(val startId: Long) : Route {
        override val key = "trash-viewer/$startId"
    }

    data class EditGif(val item: MediaItem) : Route {
        override val key = "edit-gif/${item.id}"
    }

    data object Locked : Route {
        override val key = "locked"
    }

    /** Picks the items to put in a new album named [name]. */
    data class NewAlbum(val name: String) : Route {
        override val key = "new-album/$name"
    }
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current get() = stack.last()
    fun push(route: Route) = stack.add(route)
    fun pop(): Route? = if (stack.size > 1) stack.removeAt(stack.lastIndex) else null
}

private val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

enum class Access { None, Partial, Full }

@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    fun currentAccess(): Access {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 ->
                if (granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)) Access.Full
                else if (Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) Access.Partial
                else Access.None
            else -> if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.Full else Access.None
        }
    }

    var access by remember { mutableStateOf(currentAccess()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = currentAccess()
        if (access != Access.None) vm.reload()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val previous = access
        access = currentAccess()
        if (access != previous && access != Access.None) vm.reload()
        else if (access != Access.None) vm.refresh()
    }
    LaunchedEffect(Unit) {
        if (access == Access.None) launcher.launch(mediaPermissions)
    }
    LaunchedEffect(access) {
        if (access != Access.None) vm.start()
    }

    if (access == Access.None) {
        PermissionScreen { launcher.launch(mediaPermissions) }
        return
    }

    val nav = remember { Navigator() }
    var homeTab by rememberSaveable { mutableIntStateOf(AppSettings.current.startTab) }
    val holder = rememberSaveableStateHolder()
    // Leaving the app locks the locked folder again, and closes the screens showing it
    val inLocked = Route.Locked in nav.stack
    LaunchedEffect(inLocked) { if (!inLocked) vm.relock() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (vm.authenticating) return@LifecycleEventEffect
        vm.relock()
        val first = nav.stack.indexOfFirst { it == Route.Locked }
        if (first > 0) while (nav.stack.size > first) nav.stack.removeAt(nav.stack.lastIndex).let { holder.removeState(it.key) }
    }

    // Predictive back: during the system back swipe the current screen shrinks and follows the finger,
    // then closes when the swipe is released (or springs back if it's cancelled)
    val backProgress = remember { Animatable(0f) }
    var backFromLeft by remember { mutableStateOf(true) }
    var backKey by remember { mutableStateOf<String?>(null) }
    var backActive by remember { mutableStateOf(false) }
    fun pop() = nav.pop()?.let { holder.removeState(it.key) }
    // Back buttons: no swipe animation on the closing screen
    val back: () -> Unit = { backKey = null; pop() }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = nav.stack.size > 1) { events ->
        // Reset first: the back button (3-button navigation) sends no progress at all
        backProgress.snapTo(0f)
        backKey = nav.current.key
        backActive = true
        try {
            events.collect { event ->
                backFromLeft = event.swipeEdge != BackEventCompat.EDGE_RIGHT
                backProgress.snapTo(event.progress)
            }
            backActive = false
            pop()
        } catch (e: CancellationException) {
            scope.launch {
                backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                backActive = false
            }
            throw e
        }
    }

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            // Springs instead of fixed durations: smoother at high refresh rates, and interruptible
            (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.94f)) togetherWith
                fadeOut(tween(90))
        },
        label = "nav",
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
    ) { route ->
        // The screen being swiped back keeps its shape while it fades out, the one below isn't affected
        val swiped = route.key == backKey &&
            (backActive || transition.targetState == EnterExitState.PostExit)
        Box(
            if (!swiped) Modifier else Modifier.graphicsLayer {
                val p = backProgress.value
                val scale = 1f - 0.1f * p
                scaleX = scale
                scaleY = scale
                translationX = (if (backFromLeft) 1f else -1f) * p * 24.dp.toPx()
                shape = RoundedCornerShape(32.dp * p)
                clip = true
            },
        ) {
        holder.SaveableStateProvider(route.key) {
            when (route) {
                Route.Home -> HomeScreen(
                    vm = vm,
                    tab = homeTab,
                    onTabChange = { homeTab = it },
                    partialAccess = access == Access.Partial,
                    onRequestAccess = { launcher.launch(mediaPermissions) },
                    onOpenMedia = { nav.push(Route.Viewer(null, it.id)) },
                    onOpenAlbum = { nav.push(Route.Album(it.id, it.name)) },
                    onOpenTrash = { nav.push(Route.Trash) },
                    onOpenLocked = { nav.push(Route.Locked) },
                    onNewAlbum = { nav.push(Route.NewAlbum(it)) },
                    onOpenInCollection = { id, name, item ->
                        if (item != null) nav.push(Route.Viewer(id, item.id)) else nav.push(Route.Album(id, name))
                    },
                )
                is Route.Album -> AlbumScreen(
                    vm = vm,
                    bucketId = route.id,
                    name = route.name,
                    onBack = back,
                    onOpenMedia = { nav.push(Route.Viewer(route.id, it.id)) },
                )
                is Route.Viewer -> {
                    if (route.bucketId == LOCKED_ID) SecureWindow()
                    ViewerScreen(
                    vm = vm,
                    bucketId = route.bucketId,
                    startId = route.startId,
                    onBack = back,
                    onEdit = { item ->
                        nav.push(editRouteFor(item))
                    },
                )
                }
                Route.Trash -> TrashScreen(vm = vm, onBack = back, onOpen = { nav.push(Route.TrashViewer(it.id)) })
                is Route.TrashViewer -> {
                    val trash by vm.trash.collectAsStateWithLifecycle()
                    ViewerScreen(
                        vm = vm,
                        bucketId = null,
                        startId = route.startId,
                        onBack = back,
                        onEdit = {},
                        items = trash,
                        trashMode = true,
                    )
                }
                is Route.EditPhoto -> PhotoEditorScreen(item = route.item, onClose = back)
                is Route.EditVideo -> VideoEditorScreen(item = route.item, onClose = back)
                is Route.EditGif -> GifEditorScreen(item = route.item, onClose = back)
                Route.Locked -> LockedScreen(vm = vm, onBack = back, onOpen = { nav.push(Route.Viewer(LOCKED_ID, it.id)) })
                is Route.NewAlbum -> NewAlbumScreen(vm = vm, name = route.name, onClose = back)
            }
        }
        }
    }
}

fun editRouteFor(item: MediaItem): Route = when {
    item.isVideo -> Route.EditVideo(item)
    item.mimeType.equals("image/gif", ignoreCase = true) -> Route.EditGif(item)
    else -> Route.EditPhoto(item)
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.PhotoLibrary, null,
                Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.perm_text),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) { Text(stringResource(R.string.perm_button)) }
        }
    }
}
