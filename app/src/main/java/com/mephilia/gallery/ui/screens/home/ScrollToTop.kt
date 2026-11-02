package com.mephilia.gallery.ui.screens.home

import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Beyond this many items from the top, the scroll first jumps this close and only animates the rest,
 * so going back to the top doesn't take ages in a big library.
 */
private const val SCROLL_TO_TOP_JUMP = 60

/** Height offset of a fully expanded top app bar. */
private const val EXPANDED_BAR_OFFSET = 0f

/**
 * Scrolls [grid] back to the top and expands the large title [bar] again, each time [requests] emits.
 *
 * A new request cancels the animations still running for the previous one. The bar is expanded by hand
 * because a scroll made from code doesn't go through the nested scroll connection that normally drives it.
 * When the grid has no items (loading, empty), only the bar is expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollToTopOn(requests: Flow<Unit>, grid: LazyGridState, bar: TopAppBarState) {
    LaunchedEffect(requests, grid, bar) {
        requests.collectLatest {
            coroutineScope {
                launch { animate(bar.heightOffset, EXPANDED_BAR_OFFSET) { value, _ -> bar.heightOffset = value } }
                if (grid.layoutInfo.totalItemsCount > 0) {
                    if (grid.firstVisibleItemIndex > SCROLL_TO_TOP_JUMP) grid.scrollToItem(SCROLL_TO_TOP_JUMP)
                    grid.animateScrollToItem(0)
                }
            }
        }
    }
}
