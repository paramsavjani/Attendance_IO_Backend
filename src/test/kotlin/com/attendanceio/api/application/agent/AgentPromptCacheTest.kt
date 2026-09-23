package com.attendanceio.api.application.agent

import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cache must never be the reason a turn fails: without a key, without a model, or with the
 * feature switched off it simply reports "not cached" and the caller sends the prefix inline.
 */
class AgentPromptCacheTest {
    private val mapper: ObjectMapper = tools.jackson.databind.json.JsonMapper.builder().build()

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
        val cache = AgentPromptCache(true, 30, 1024, "", "gemini-3.1-flash-lite", mapper)
        assertTrue(!cache.isEnabled())
        assertNull(cache.nameFor("prompt", listOf(callback("a"))))
    }

    @Test
    fun `disabled by configuration`() {
        val cache = AgentPromptCache(false, 30, 1024, "key", "gemini-3.1-flash-lite", mapper)
        assertTrue(!cache.isEnabled())
        assertNull(cache.nameFor("prompt", listOf(callback("a"))))
    }

    @Test
    fun `no tools means nothing worth caching`() {
        val cache = AgentPromptCache(true, 30, 1024, "key", "gemini-3.1-flash-lite", mapper)
        assertTrue(cache.isEnabled())
        assertNull(cache.nameFor("prompt", emptyList()))
    }

    @Test
    fun `a creation failure is reported as not cached, never thrown`() {
        // A syntactically valid but unusable key: the HTTP call fails and the turn falls back inline.
        val cache = AgentPromptCache(true, 30, 1024, "not-a-real-key", "gemini-3.1-flash-lite", mapper)
        assertNull(cache.nameFor("prompt", listOf(callback("a"))))
    }
}
