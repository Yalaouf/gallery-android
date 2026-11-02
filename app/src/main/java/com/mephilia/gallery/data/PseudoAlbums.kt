package com.mephilia.gallery.data

/** Album id of the locked folder. Pseudo album ids are far below any real folder id, so they can't collide. */
const val LOCKED_ID = Long.MIN_VALUE

/** Album id of the favorites. */
const val FAVORITES_ID = Long.MIN_VALUE + 1

/** Album id of "on this day": photos taken on today's date in previous years. */
const val ON_THIS_DAY_ID = Long.MIN_VALUE + 2

/** Album id of all the photos. */
const val PHOTOS_ID = Long.MIN_VALUE + 3

/** Album id of all the videos. */
const val VIDEOS_ID = Long.MIN_VALUE + 4
