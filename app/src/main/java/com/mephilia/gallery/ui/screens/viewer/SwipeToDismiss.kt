package com.mephilia.gallery.ui.screens.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** How far the item must be dragged for the viewer to close when it's released. */
private val DISMISS_DISTANCE = 120.dp

/** How fast a shorter fling must be (per second) to close the viewer anyway. */
private val DISMISS_VELOCITY = 1000.dp

/** The background is fully transparent once the item is dragged this many times the dismiss distance. */
private const val FULL_FADE_DISTANCES = 3f

/** How much the item shrinks when the background is fully faded out. */
private const val MAX_SHRINK = 0.25f

/**
 * State of the "swipe up or down to close" gesture of the viewer: how far the item is dragged,
 * and how much the background is faded and the item shrunk as a result.
 */
@Stable
internal class SwipeToDismissState(
    internal val distancePx: Float,
    internal val velocityPx: Float,
) {
    /** Animates the vertical offset of the item. */
    internal val animatable = Animatable(0f)

    /** Set once the viewer is closing, so a second gesture can't close it twice. */
    internal var dismissing = false

    /** Vertical offset of the item, in pixels. */
    val offset: Float get() = animatable.value

    /** Whether the item is away from its resting place (dragged, or springing back). */
    val isDragged: Boolean get() = animatable.value != 0f

    /** How faded out the black background is, from 0 (opaque) to 1 (transparent). */
    val fade: Float get() = (abs(offset) / (distancePx * FULL_FADE_DISTANCES)).coerceIn(0f, 1f)

    /** Scale of the item, which shrinks as the background fades. */
    val scale: Float get() = 1f - MAX_SHRINK * fade
}

/** Remembers a [SwipeToDismissState] with the dismiss distance and speed converted for this screen's density. */
@Composable
internal fun rememberSwipeToDismissState(): SwipeToDismissState {
    val density = LocalDensity.current
    return remember(density) {
        with(density) { SwipeToDismissState(DISMISS_DISTANCE.toPx(), DISMISS_VELOCITY.toPx()) }
    }
}

/**
 * Lets the user drag the content up or down to close the viewer: it follows the finger, and closes ([onDismiss])
 * when released far enough or flung fast enough in the direction of the drag. Otherwise it springs back
 * and [onCancel] is called.
 *
 * Zoomed-in pictures and pinches consume their moves, so they never trigger this gesture.
 */
internal fun Modifier.swipeToDismiss(
    state: SwipeToDismissState,
    onDragStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
): Modifier = pointerInput(state) {
    coroutineScope {
        val velocity = VelocityTracker()
        val offset = state.animatable

        fun springBack(animate: suspend () -> Unit) {
            onCancel()
            launch { animate() }
        }

        detectVerticalDragGestures(
            onDragStart = {
                velocity.resetTracking()
                onDragStart()
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                launch { offset.snapTo(offset.value + dragAmount) }
            },
            onDragEnd = {
                val speed = velocity.calculateVelocity().y
                val flung = abs(speed) > state.velocityPx && sign(speed) == sign(offset.value)
                if (!state.dismissing && (abs(offset.value) > state.distancePx || flung)) {
                    state.dismissing = true
                    onDismiss()
                } else {
                    springBack { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                }
            },
            onDragCancel = { springBack { offset.animateTo(0f) } },
        )
    }
}
