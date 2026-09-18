package com.attendanceio.api.application.agent

import com.attendanceio.api.model.agent.AgentMessageRole
import java.time.Instant

/** One message as kept in memory. Only what the model needs to be replayed, plus display metadata. */
data class StoredAgentMessage(
    val role: AgentMessageRole,
    val content: String,
    val at: Instant,
    /** Set on assistant messages: the turn that produced it. */
    val turnId: String? = null,
    val toolNames: List<String>? = null,
    val latencyMs: Long? = null
)

/**
 * Conversation memory for the chat agent: the recent turns replayed to the model within one
 * session. Threads are scoped to their owner — every operation takes the caller's email and a
 * conversation from another user reads as empty. Stored history is never shown to users.
 */
interface AgentConversationMemory {
    /** The most recent [limit] messages of the thread, oldest first. Empty if unknown. */
    fun load(userEmail: String, conversationId: String, limit: Int): List<StoredAgentMessage>

    /** Appends messages in order, creating the thread (owned by [owner]) on first use. */
    fun append(owner: AgentCaller, conversationId: String, messages: List<StoredAgentMessage>)
}
