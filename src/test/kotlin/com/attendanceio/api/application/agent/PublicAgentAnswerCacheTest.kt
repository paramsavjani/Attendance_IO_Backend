package com.attendanceio.api.application.agent

import com.attendanceio.api.application.agent.`public`.PublicAgentAnswerCache
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The demo's suggested questions are asked over and over, so they are answered once and replayed —
 * which only stays correct while the key means "the same question asked cold". These pin the two
 * halves of that: what counts as the same question, and what is never eligible at all.
 */
class PublicAgentAnswerCacheTest {
    private fun cache(enabled: Boolean = true, ttlHours: Long = 24, maxEntries: Int = 500) =
        PublicAgentAnswerCache(enabled, ttlHours, maxEntries)

    private val suggestion = "Where do DAU graduates work?"

    @Test
    fun `a repeated opening question is answered from memory`() {
        val cache = cache()
        cache.store(suggestion, "Mostly in Bengaluru and Pune.", firstTurn = true)

        assertEquals("Mostly in Bengaluru and Pune.", cache.lookup(suggestion, firstTurn = true))
    }

    @Test
    fun `case, spacing and a trailing question mark do not make it a different question`() {
        val cache = cache()
        cache.store(suggestion, "An answer.", firstTurn = true)

        assertEquals("An answer.", cache.lookup("where do DAU   graduates work", firstTurn = true))
        assertEquals("An answer.", cache.lookup("  Where do DAU graduates work?  ", firstTurn = true))
    }

    @Test
    fun `a different question is not served someone else's answer`() {
        val cache = cache()
        cache.store("Which companies recruit from DAU?", "A list of companies.", firstTurn = true)

        assertNull(cache.lookup("Which programmes does DAU offer?", firstTurn = true))
    }

    @Test
    fun `a follow-up is neither served nor stored, because its answer depends on the thread`() {
        val cache = cache()
        cache.store(suggestion, "An answer.", firstTurn = true)
        // Same words, but asked mid-thread: the visitor means something the key cannot see.
        assertNull(cache.lookup(suggestion, firstTurn = false))

        cache.store("and who runs it?", "Some club convener.", firstTurn = false)
        assertNull(cache.lookup("and who runs it?", firstTurn = true))
    }

    @Test
    fun `a greeting is too short to be worth replaying a day later`() {
        val cache = cache()
        cache.store("hi", "Hello — ask me about DAU.", firstTurn = true)

        assertNull(cache.lookup("hi", firstTurn = true))
    }

    @Test
    fun `an empty answer is never stored`() {
        val cache = cache()
        cache.store(suggestion, "   ", firstTurn = true)

        assertNull(cache.lookup(suggestion, firstTurn = true))
    }

    @Test
    fun `an expired entry is not served`() {
        val cache = cache(ttlHours = 0)
        cache.store(suggestion, "Yesterday's answer.", firstTurn = true)

        assertNull(cache.lookup(suggestion, firstTurn = true))
    }

    @Test
    fun `disabled, it holds nothing and serves nothing`() {
        val cache = cache(enabled = false)
        cache.store(suggestion, "An answer.", firstTurn = true)

        assertNull(cache.lookup(suggestion, firstTurn = true))
    }

    @Test
    fun `the coldest question is dropped once the cache is full`() {
        val cache = cache(maxEntries = 2)
        cache.store("Which companies recruit from DAU?", "first", firstTurn = true)
        cache.store("What scholarships does DAU offer?", "second", firstTurn = true)
        // Asking the first one again makes the second the coldest.
        cache.lookup("Which companies recruit from DAU?", firstTurn = true)
        cache.store("Which programmes does DAU offer?", "third", firstTurn = true)

        assertEquals("first", cache.lookup("Which companies recruit from DAU?", firstTurn = true))
        assertEquals("third", cache.lookup("Which programmes does DAU offer?", firstTurn = true))
        assertNull(cache.lookup("What scholarships does DAU offer?", firstTurn = true))
    }
}
