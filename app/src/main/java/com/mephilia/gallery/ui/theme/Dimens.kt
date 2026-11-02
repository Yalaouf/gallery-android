package com.mephilia.gallery.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale used for paddings and gaps across the app, from the smallest to the largest step.
 * Using these instead of raw values keeps the layout rhythm consistent between screens.
 */
object Spacing {
    /** Hairline gap, e.g. between a title and its subtitle. */
    val xxs = 2.dp

    /** Tight gap between closely related elements. */
    val xs = 4.dp

    /** Small gap, the most common one between items. */
    val s = 8.dp

    /** Medium gap, e.g. between an icon and its label. */
    val m = 12.dp

    /** Standard screen edge padding. */
    val l = 16.dp

    /** Large gap between sections. */
    val xl = 24.dp

    /** Extra large gap, for roomy layouts such as empty states and dialogs. */
    val xxl = 32.dp
}

/** Sizes and radii shared by several screens. */
object Dimens {
    /** Corner radius of album, collection and trash cards. */
    val cardCorner = 20.dp

    /** Corner radius of banners and smaller rounded surfaces. */
    val bannerCorner = 18.dp

    /** Smallest width of a card in the album and collection grids; the grid fits as many columns as it can. */
    val cardMinWidth = 160.dp

    /** Horizontal gap between cards in the album and collection grids. */
    val cardGridHorizontalGap = 14.dp

    /** Vertical gap between cards in the album and collection grids. */
    val cardGridVerticalGap = 18.dp

    /** Size of a large icon shown alone in a card or an empty state. */
    val largeIcon = 48.dp

    /** Space kept free at the bottom of scrolling content, so the floating bars never hide the last items. */
    val floatingBarClearance = 104.dp

    /** Space kept free under a grid for a floating actions bar alone (no navigation bar), above the system bar. */
    val actionsBarClearance = 88.dp

    /** Gap between a floating bar (navigation, selection actions) and the bottom of the screen. */
    val floatingBarBottomMargin = 16.dp

    /** Shadow under a floating bar, which lifts it above the content scrolling behind it. */
    val floatingBarShadow = 16.dp

    /** Tonal elevation of a floating bar's surface. */
    val floatingBarElevation = 6.dp

    /** Size of a folder thumbnail in folder lists, such as the move and copy sheet. */
    val listThumbnail = 48.dp

    /** Corner radius of a folder thumbnail in folder lists. */
    val listThumbnailCorner = 12.dp
}
