package com.mephilia.gallery.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Events kept while nobody is collecting them yet, so a burst of changes isn't lost. */
private const val EVENT_BUFFER = 16

/**
 * Changes made by the app itself, so the gallery can update instantly instead of waiting for MediaStore's
 * change notification and the reload that follows.
 */
object MediaEvents {
    /** A change made by the app. */
    sealed interface Event {
        /** Items were deleted, trashed, hidden or restored: they leave the lists they were in. */
        data class Removed(val ids: Set<Long>) : Event

        /** The file was rewritten in place: its thumbnail and image must be reloaded. */
        data class Replaced(val id: Long) : Event

        /** New or moved files: reload now. */
        data object Changed : Event

        /** Items were added to or removed from the favorites. */
        data class Favorited(val ids: Set<Long>, val favorite: Boolean) : Event
    }

    /** Where the events are sent. */
    private val flow = MutableSharedFlow<Event>(extraBufferCapacity = EVENT_BUFFER)

    /** The changes, as they happen. */
    val events: SharedFlow<Event> = flow

    /** Tells that the items with [ids] are gone. */
    fun removed(ids: Collection<Long>) = flow.tryEmit(Event.Removed(ids.toSet()))

    /** Tells that the file of item [id] was rewritten. */
    fun replaced(id: Long) = flow.tryEmit(Event.Replaced(id))

    /** Tells that files were added or moved. */
    fun changed() = flow.tryEmit(Event.Changed)

    /** Tells that the items with [ids] were added to the favorites, or removed if not [favorite]. */
    fun favorited(ids: Collection<Long>, favorite: Boolean) = flow.tryEmit(Event.Favorited(ids.toSet(), favorite))
}
