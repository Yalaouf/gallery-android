package com.mephilia.gallery.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** How much the screen shrinks at the end of the back swipe. */
private const val MAX_SHRINK = 0.1f

/** How far the screen moves away from the swiped edge at the end of the back swipe. */
private val MAX_SHIFT = 24.dp

/** Corner radius the screen gets at the end of the back swipe. */
private val MAX_CORNER = 32.dp

/**
 * State of the predictive back animation: during the system back swipe, the current screen shrinks and follows
 * the finger, then closes when the swipe is released, or springs back if it's cancelled.
 */
@Stable
class PredictiveBackState {
    /** How far the swipe went, from 0 to 1. */
    internal val progress = Animatable(0f)

    /** Whether the swipe started from the left edge (the screen then moves right). */
    internal var fromLeft by mutableStateOf(true)

    /** Key of the screen being swiped back, or null when none is (e.g. closed with an on-screen back button). */
    var swipedKey by mutableStateOf<String?>(null)

    /** Whether a swipe is in progress. */
    var active by mutableStateOf(false)
}

/**
 * Handles the system back gesture while [enabled]: animates the screen with [currentKey] through [state],
 * then calls [onBack] once the swipe is released.
 */
@Composable
fun PredictiveBack(state: PredictiveBackState, enabled: Boolean, currentKey: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = enabled) { events ->
        state.progress.snapTo(0f)
        state.swipedKey = currentKey
        state.active = true
        try {
            events.collect { event ->
                state.fromLeft = event.swipeEdge != BackEventCompat.EDGE_RIGHT
                state.progress.snapTo(event.progress)
            }
            state.active = false
            onBack()
        } catch (e: CancellationException) {
            scope.launch {
                state.progress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                state.active = false
            }
            throw e
        }
    }
}

/** Remembers a [PredictiveBackState]. */
@Composable
fun rememberPredictiveBackState() = remember { PredictiveBackState() }

/**
 * Shrinks, shifts and rounds the content according to the back swipe in [state], when [swiped].
 * The progress is reset at the start of every swipe, because the back button (3-button navigation)
 * sends no progress at all.
 */
fun Modifier.predictiveBack(state: PredictiveBackState, swiped: Boolean): Modifier =
    if (!swiped) this else graphicsLayer {
        val progress = state.progress.value
        val scale = 1f - MAX_SHRINK * progress
        scaleX = scale
        scaleY = scale
        translationX = (if (state.fromLeft) 1f else -1f) * progress * MAX_SHIFT.toPx()
        shape = RoundedCornerShape(MAX_CORNER * progress)
        clip = true
    }
