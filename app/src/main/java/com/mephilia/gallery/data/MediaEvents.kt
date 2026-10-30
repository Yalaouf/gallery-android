package com.mephilia.gallery.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Changes made by the app itself, so the gallery can update instantly instead of waiting
 * for MediaStore's change notification and the reload that follows.
 */
object MediaEvents {
    sealed interface Event {
        data class Removed(val ids: Set<Long>) : Event
        /** The file was rewritten in place: its thumbnail and image must be reloaded. */
        data class Replaced(val id: Long) : Event
        /** New or moved files: reload now. */
        data object Changed : Event
    }

    private val flow = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = flow

    fun removed(ids: Collection<Long>) = flow.tryEmit(Event.Removed(ids.toSet()))
    fun replaced(id: Long) = flow.tryEmit(Event.Replaced(id))
    fun changed() = flow.tryEmit(Event.Changed)
}
