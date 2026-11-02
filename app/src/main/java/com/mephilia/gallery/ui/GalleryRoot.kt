package com.mephilia.gallery.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.LOCKED_ID
import com.mephilia.gallery.ui.editor.GifEditorScreen
import com.mephilia.gallery.ui.editor.PhotoEditorScreen
import com.mephilia.gallery.ui.editor.VideoEditorScreen
import com.mephilia.gallery.ui.screens.AlbumScreen
import com.mephilia.gallery.ui.screens.LockedScreen
import com.mephilia.gallery.ui.screens.NewAlbumScreen
import com.mephilia.gallery.ui.screens.SecureWindow
import com.mephilia.gallery.ui.screens.TrashScreen
import com.mephilia.gallery.ui.screens.home.HomeScreen
import com.mephilia.gallery.ui.screens.home.HomeTab
import com.mephilia.gallery.ui.screens.viewer.ViewerScreen

/** Damping of the zoom of a screen opening: slightly bouncy. */
private const val OPEN_DAMPING = 0.85f

/** Scale a screen starts from when it opens, growing to full size. */
private const val OPEN_INITIAL_SCALE = 0.94f

/** How long the screen being left takes to fade out, in milliseconds. */
private const val CLOSE_FADE_MS = 90

/**
 * The app's root: asks for access to photos, then shows the screen on top of the back stack.
 *
 * Screens open with a fade and a slight zoom (springs rather than fixed durations: smoother at high refresh
 * rates, and interruptible), and close with the predictive back animation. Each screen's state is kept while
 * it's in the back stack, and dropped once it's closed.
 *
 * Leaving the app locks the locked folder again and closes the screens showing it, unless the unlock prompt
 * is what hid the app.
 */
@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(currentAccess(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = currentAccess(context)
        if (access != Access.None) vm.reload()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val previous = access
        access = currentAccess(context)
        when {
            access == Access.None -> Unit
            access != previous -> vm.reload()
            else -> vm.refresh()
        }
    }
    LaunchedEffect(Unit) { if (access == Access.None) launcher.launch(mediaPermissions) }
    LaunchedEffect(access) { if (access != Access.None) vm.start() }

    if (access == Access.None) {
        PermissionScreen { launcher.launch(mediaPermissions) }
        return
    }

    val nav = remember { Navigator() }
    val states = rememberSaveableStateHolder()
    val backState = rememberPredictiveBackState()
    var homeTab by rememberSaveable { mutableIntStateOf(AppSettings.current.startTab) }

    val inLocked = Route.Locked in nav.stack
    LaunchedEffect(inLocked) { if (!inLocked) vm.relock() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (vm.authenticating) return@LifecycleEventEffect
        vm.relock()
        closeLockedScreens(nav, states)
    }

    fun pop() = nav.pop()?.let { states.removeState(it.key) }
    val back: () -> Unit = {
        backState.swipedKey = null
        pop()
    }
    PredictiveBack(backState, enabled = nav.stack.size > 1, currentKey = nav.current.key, onBack = ::pop)

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(spring(dampingRatio = OPEN_DAMPING, stiffness = Spring.StiffnessMediumLow), initialScale = OPEN_INITIAL_SCALE)) togetherWith
                fadeOut(tween(CLOSE_FADE_MS))
        },
        label = "navigation",
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
    ) { route ->
        val swiped = route.key == backState.swipedKey &&
            (backState.active || transition.targetState == EnterExitState.PostExit)
        Box(Modifier.predictiveBack(backState, swiped)) {
            states.SaveableStateProvider(route.key) {
                RouteContent(
                    route = route,
                    vm = vm,
                    nav = nav,
                    homeTab = HomeTab.fromIndex(homeTab),
                    onHomeTabChange = { homeTab = it.ordinal },
                    partialAccess = access == Access.Partial,
                    onRequestAccess = { launcher.launch(mediaPermissions) },
                    onBack = back,
                )
            }
        }
    }
}

/** Closes the locked folder and every screen opened from it, dropping their saved state. */
private fun closeLockedScreens(nav: Navigator, states: SaveableStateHolder) {
    val first = nav.stack.indexOfFirst { it == Route.Locked }
    if (first <= 0) return
    while (nav.stack.size > first) states.removeState(nav.stack.removeAt(nav.stack.lastIndex).key)
}

/** The screen for [route], wired to the navigation. */
@Composable
private fun RouteContent(
    route: Route,
    vm: GalleryViewModel,
    nav: Navigator,
    homeTab: HomeTab,
    onHomeTabChange: (HomeTab) -> Unit,
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    onBack: () -> Unit,
) {
    when (route) {
        Route.Home -> HomeScreen(
            vm = vm,
            tab = homeTab,
            onTabChange = onHomeTabChange,
            partialAccess = partialAccess,
            onRequestAccess = onRequestAccess,
            onOpenMedia = { nav.push(Route.Viewer(null, it.id)) },
            onOpenAlbum = { nav.push(Route.Album(it.id, it.name)) },
            onOpenTrash = { nav.push(Route.Trash) },
            onOpenLocked = { nav.push(Route.Locked) },
            onNewAlbum = { nav.push(Route.NewAlbum(it)) },
            onOpenInCollection = { id, name, item ->
                nav.push(if (item != null) Route.Viewer(id, item.id) else Route.Album(id, name))
            },
        )
        is Route.Album -> AlbumScreen(
            vm = vm,
            bucketId = route.id,
            name = route.name,
            onBack = onBack,
            onOpenMedia = { nav.push(Route.Viewer(route.id, it.id)) },
        )
        is Route.Viewer -> {
            if (route.bucketId == LOCKED_ID) SecureWindow()
            ViewerScreen(
                vm = vm,
                bucketId = route.bucketId,
                startId = route.startId,
                onBack = onBack,
                onEdit = { nav.push(editRouteFor(it)) },
            )
        }
        Route.Trash -> TrashScreen(vm = vm, onBack = onBack, onOpen = { nav.push(Route.TrashViewer(it.id)) })
        is Route.TrashViewer -> {
            val trash by vm.trash.collectAsStateWithLifecycle()
            ViewerScreen(
                vm = vm,
                bucketId = null,
                startId = route.startId,
                onBack = onBack,
                onEdit = {},
                items = trash,
                trashMode = true,
            )
        }
        is Route.EditPhoto -> PhotoEditorScreen(item = route.item, onClose = onBack)
        is Route.EditVideo -> VideoEditorScreen(item = route.item, onClose = onBack)
        is Route.EditGif -> GifEditorScreen(item = route.item, onClose = onBack)
        Route.Locked -> LockedScreen(vm = vm, onBack = onBack, onOpen = { nav.push(Route.Viewer(LOCKED_ID, it.id)) })
        is Route.NewAlbum -> NewAlbumScreen(vm = vm, name = route.name, onClose = onBack)
    }
}
