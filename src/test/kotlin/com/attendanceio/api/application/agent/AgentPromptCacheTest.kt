package com.attendanceio.api.application.agent

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cache must never be the reason a turn fails: without a key, without a model, or with the
 * feature switched off it simply reports "not cached" and the caller sends the prefix inline.
 */
class AgentPromptCacheTest {
    private val mapper: ObjectMapper = JsonMapper.builder().build()

    private fun callback(name: String): ToolCallback {
        val definition = mock(ToolDefinition::class.java)
        `when`(definition.name()).thenReturn(name)
        `when`(definition.description()).thenReturn("does $name")
        `when`(definition.inputSchema()).thenReturn(
            """{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{},"required":[],"additionalProperties":false}"""
        )
        val callback = mock(ToolCallback::class.java)
        `when`(callback.toolDefinition).thenReturn(definition)
        return callback
    }

    @Test
    fun `disabled without an api key`() {
        val cache = AgentPromptCache(true, 30, 1024, 1, "", "gemini-3.1-flash-lite", mapper)
        assertTrue(!cache.isEnabled())
        assertNull(cache.nameFor("prompt", listOf(callback("a"))))
    }

    @Test
    fun `disabled by configuration`() {
        val cache = AgentPromptCache(false, 30, 1024, 1, "key", "gemini-3.1-flash-lite", mapper)
        assertTrue(!cache.isEnabled())
        assertNull(cache.nameFor("prompt", listOf(callback("a"))))
    }

    @Test
    fun `no tools means nothing worth caching`() {
        val cache = AgentPromptCache(true, 30, 1024, 1, "key", "gemini-3.1-flash-lite", mapper)
        assertTrue(cache.isEnabled())
        assertNull(cache.nameFor("prompt", emptyList()))
    }

    @Test
    fun `a creation failure is reported as not cached, never thrown`() {
        // A syntactically valid but unusable key: the HTTP call fails and the turn falls back inline.
        val cache = AgentPromptCache(true, 30, 1024, 1, "not-a-real-key", "gemini-3.1-flash-lite", mapper)
        assertNull(cache.nameFor("prompt", listOf(callback("a"))))
    }

    /**
     * Storage is billed for a cache's whole TTL whether it is used again or not, and the router's tool
     * combinations are mostly one-offs — so a prefix has to come back before it earns one.
     */
    private class Counting(warmupUses: Int, private val ttlMinutes: Long = 5) :
        AgentPromptCache(true, ttlMinutes, 1024, warmupUses, "key", "gemini-3.1-flash-lite", JsonMapper.builder().build()) {
        /** How often Gemini would have been asked to store something. */
        var creations = 0

        override fun create(systemPrompt: String, declarations: List<Map<String, Any?>>): Entry {
            creations++
            return Entry("cachedContents/fake-$creations", 2_000, Instant.now().plusSeconds(ttlMinutes * 60))
        }
    }

    @Test
    fun `a prefix seen once is not cached - it pays storage for a turn that never repeats`() {
        val cache = Counting(warmupUses = 2)
        assertNull(cache.nameFor("prompt", listOf(callback("a"))))
        assertEquals(0, cache.creations, "nothing should have been stored on first sight")
    }

    @Test
    fun `the second turn with the same prefix earns the cache`() {
        val cache = Counting(warmupUses = 2)
        val tools = listOf(callback("a"), callback("b"))
        assertNull(cache.nameFor("prompt", tools))
        assertEquals("cachedContents/fake-1", cache.nameFor("prompt", tools))
        assertEquals(1, cache.creations)
    }

    @Test
    fun `a third turn reuses the live cache instead of making another`() {
        val cache = Counting(warmupUses = 2)
        val tools = listOf(callback("a"))
        cache.nameFor("prompt", tools)
        val first = cache.nameFor("prompt", tools)
        assertEquals(first, cache.nameFor("prompt", tools))
        assertEquals(1, cache.creations)
    }

    @Test
    fun `a prefix that has been cached before skips the warm-up when its cache expires`() {
        val cache = Counting(warmupUses = 2)
        val tools = listOf(callback("a"))
        cache.nameFor("prompt", tools)
        val name = cache.nameFor("prompt", tools)!!
        cache.forget(name)   // as if the TTL had run out
        assertEquals("cachedContents/fake-2", cache.nameFor("prompt", tools), "a proven prefix re-caches at once")
        assertEquals(2, cache.creations)
    }

    @Test
    fun `different tool combinations warm up independently`() {
        val cache = Counting(warmupUses = 2)
        assertNull(cache.nameFor("prompt", listOf(callback("a"))))
        assertNull(cache.nameFor("prompt", listOf(callback("b"))))
        assertEquals(0, cache.creations, "two different prefixes seen once each are still two one-offs")
    }
}
