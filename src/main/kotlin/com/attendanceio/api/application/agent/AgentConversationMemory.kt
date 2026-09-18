package com.attendanceio.api.application.agent

import com.attendanceio.api.model.agent.AgentMessageRole
import java.time.Instant

/** One message as kept in short-term memory. Only what the model needs to be replayed, plus display metadata. */
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
 * Short-term conversation memory for the chat agent. Threads are scoped to their owner: every
 * operation takes the caller's email and a conversation from another user reads as empty.
 *
 * Best-effort by contract — nothing here is a system of record, and a missing thread is simply
 * an empty history.
 */
interface AgentConversationMemory {
    /** The most recent [limit] messages of the thread, oldest first. Empty if unknown. */
    fun load(userEmail: String, conversationId: String, limit: Int): List<StoredAgentMessage>

    /** The whole thread as stored, oldest first. */
    fun loadAll(userEmail: String, conversationId: String): List<StoredAgentMessage>

    /** Appends messages in order and refreshes the thread's expiry. */
    fun append(userEmail: String, conversationId: String, messages: List<StoredAgentMessage>)
}
