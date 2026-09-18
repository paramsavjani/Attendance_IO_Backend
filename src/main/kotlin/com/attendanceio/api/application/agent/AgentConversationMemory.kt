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

data class StoredAgentConversation(
    val conversationId: String,
    val title: String?,
    val messageCount: Int,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

/**
 * Conversation memory for the chat agent. Threads are scoped to their owner: every operation
 * takes the caller's email and a conversation from another user reads as empty.
 */
interface AgentConversationMemory {
    /** The most recent [limit] messages of the thread, oldest first. Empty if unknown. */
    fun load(userEmail: String, conversationId: String, limit: Int): List<StoredAgentMessage>

    /** The whole thread as stored, oldest first. */
    fun loadAll(userEmail: String, conversationId: String): List<StoredAgentMessage>

    /** The caller's threads, most recently active first. */
    fun listConversations(userEmail: String, limit: Int): List<StoredAgentConversation>

    /** Appends messages in order, creating the thread on first use. */
    fun append(userEmail: String, conversationId: String, messages: List<StoredAgentMessage>)
}
