package com.attendanceio.api.application.agent

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.DefaultToolDefinition
import org.springframework.ai.tool.definition.ToolDefinition
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A turn that runs off a context cache declares no tools in the request — they are inside the
 * cache — so the model can name a tool the request never mentioned. Executing it then depends
 * entirely on the manager resolving the name through its resolver, which Spring AI does NOT do
 * unless resolution fallback is switched on (it is off by default).
 *
 * This is exactly the wiring that broke the assistant in production with
 * "No ToolCallback found for tool name: get_subject_class_stats", so it is pinned here.
 */
class AgentCachedToolResolutionTest {
    private val callback = object : ToolCallback {
        override fun getToolDefinition(): ToolDefinition =
            DefaultToolDefinition.builder()
                .name("get_subject_class_stats")
                .description("class stats for one subject")
                .inputSchema("""{"type":"object","properties":{},"required":[]}""")
                .build()

        override fun call(toolInput: String): String = """{"average":71.2}"""

        override fun call(toolInput: String, toolContext: org.springframework.ai.chat.model.ToolContext?): String = call(toolInput)
    }

    private fun manager(fallback: Boolean): ToolCallingManager =
        ToolCallingManager.builder()
            .toolCallbackResolver(StaticToolCallbackResolver(listOf(callback)))
            .resolutionFallbackEnabled(fallback)
            .build()

    /** Options as a cached turn builds them: a cache is named, and no tool is declared. */
    private fun promptWithoutTools(): Prompt =
        Prompt(listOf(UserMessage("class average in DSA")), ToolCallingChatOptions.builder().build())

    private fun toolCallResponse(): ChatResponse =
        ChatResponse(
            listOf(
                Generation(
                    AssistantMessage.builder()
                        .content("")
                        .toolCalls(listOf(AssistantMessage.ToolCall("call-1", "function", "get_subject_class_stats", "{}")))
                        .build()
                )
            )
        )

    @Test
    fun `a tool the request never declared still runs when resolution fallback is on`() {
        val result = manager(fallback = true).executeToolCalls(promptWithoutTools(), toolCallResponse())
        val response = result.conversationHistory().filterIsInstance<ToolResponseMessage>().single()
        assertEquals("get_subject_class_stats", response.responses.single().name())
        assertTrue(response.responses.single().responseData().contains("71.2"))
    }

    @Test
    fun `without the fallback the same call fails - the default that broke production`() {
        val failure = runCatching { manager(fallback = false).executeToolCalls(promptWithoutTools(), toolCallResponse()) }
        assertTrue(failure.isFailure)
        assertTrue(
            failure.exceptionOrNull()?.message.orEmpty().contains("get_subject_class_stats"),
            "expected the missing-callback failure, got: ${failure.exceptionOrNull()?.message}"
        )
    }
}
