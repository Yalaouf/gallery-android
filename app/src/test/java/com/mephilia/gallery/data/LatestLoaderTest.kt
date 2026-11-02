package com.mephilia.gallery.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Loads finish in whatever order the test decides, by completing their gates. */
class LatestLoaderTest {
    private val results = mutableListOf<String>()

    private fun gates() = ArrayDeque<CompletableDeferred<String>>()

    private suspend fun settle() = repeat(5) { yield() }

    @Test
    fun anOlderLoadFinishingLastDoesNotOverwriteANewerOne() = runBlocking {
        val gates = gates()
        val loader = LatestLoader(this, load = { CompletableDeferred<String>().also { gates.addLast(it) }.await() }, apply = { results += it })

        loader.reload()
        settle()
        val older = gates.removeFirst()
        loader.reload()
        settle()
        val newer = gates.removeFirst()

        newer.complete("new")
        settle()
        older.complete("old")
        settle()
        assertEquals(listOf("new"), results)
    }

    @Test
    fun cancelDropsTheResult() = runBlocking {
        val gate = CompletableDeferred<String>()
        val loader = LatestLoader(this, load = { gate.await() }, apply = { results += it })
        loader.reload()
        settle()
        loader.cancel()
        gate.complete("too late")
        settle()
        assertTrue(results.isEmpty())
        assertFalse(loader.isLoading)
    }

    @Test
    fun restartOnlyWhenALoadIsRunning() = runBlocking {
        var calls = 0
        val gates = gates()
        val loader = LatestLoader(this, load = { calls++; CompletableDeferred<String>().also { gates.addLast(it) }.await() }, apply = { results += it })

        // Nothing running: nothing to restart
        loader.restartIfLoading()
        settle()
        assertEquals(0, calls)

        loader.reload()
        settle()
        // A change happened while loading: the load starts again, and only its result counts
        loader.restartIfLoading()
        settle()
        assertEquals(2, calls)
        gates.removeFirst().complete("stale")
        gates.removeFirst().complete("fresh")
        settle()
        assertEquals(listOf("fresh"), results)
    }
}
