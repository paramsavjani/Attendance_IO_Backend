package com.attendanceio.api.model.agent

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

enum class AgentMessageRole {
    USER,
    ASSISTANT
}

/**
 * A chat turn. Send `conversationId` (from a previous META/response) to continue a thread whose
 * history the server keeps in short-term memory; omit it to start a new one.
 */
data class AgentChatRequest(
    @field:NotBlank
    @field:Size(max = 4000)
    val message: String,
    @field:Pattern(regexp = CONVERSATION_ID_PATTERN, message = "must be a UUID")
    val conversationId: String? = null
) {
    companion object {
        /** UUID only — the id becomes part of a memory key, so nothing else may pass. */
        const val CONVERSATION_ID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    }
}

data class AgentToolCallResponse(
    val name: String,
    val arguments: Map<String, Any?>,
    val durationMs: Long,
    val error: String? = null
)

data class AgentTokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
)

data class AgentChatResponse(
    /** Send back on the next turn to continue the thread. */
    val conversationId: String,
    /** Correlates the answer with the `agent=TURN_*` server log lines. */
    val turnId: String,
    val answer: String,
    val toolCalls: List<AgentToolCallResponse>,
    val latencyMs: Long,
    val usage: AgentTokenUsage?
)

enum class AgentStreamEventType {
    /** First event: the conversation id to send on the next turn, and the turn id. */
    META,

    /** A chunk of the answer text, in order. Concatenate to rebuild the reply. */
    TOKEN,

    /** Last event on success: tool calls, latency and token usage for the turn. */
    DONE,

    /** Last event on failure. Whatever text was streamed before it is partial and was not remembered. */
    ERROR
}

/** One SSE `data:` payload on `POST /api/agent/chat/stream`. Serialised as JSON. */
data class AgentStreamEvent(
    val type: AgentStreamEventType,
    val conversationId: String,
    val turnId: String,
    val text: String? = null,
    val toolCalls: List<AgentToolCallResponse>? = null,
    val latencyMs: Long? = null,
    val firstTokenMs: Long? = null,
    val usage: AgentTokenUsage? = null,
    val error: String? = null
) {
    companion object {
        fun meta(conversationId: String, turnId: String) =
            AgentStreamEvent(AgentStreamEventType.META, conversationId, turnId)

        fun token(conversationId: String, turnId: String, text: String) =
            AgentStreamEvent(AgentStreamEventType.TOKEN, conversationId, turnId, text = text)

        fun done(
            conversationId: String,
            turnId: String,
            toolCalls: List<AgentToolCallResponse>,
            latencyMs: Long,
            firstTokenMs: Long?,
            usage: AgentTokenUsage?
        ) = AgentStreamEvent(
            AgentStreamEventType.DONE, conversationId, turnId,
            toolCalls = toolCalls, latencyMs = latencyMs, firstTokenMs = firstTokenMs, usage = usage
        )

        fun error(conversationId: String, turnId: String, message: String) =
            AgentStreamEvent(AgentStreamEventType.ERROR, conversationId, turnId, error = message)
    }
}
