package com.attendanceio.api.application.agent

import com.attendanceio.api.model.agent.DMAgentMessage
import com.attendanceio.api.repository.agent.AgentConversationRepositoryAppAction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.ZoneId

/**
 * Postgres-backed [AgentConversationMemory] (`agent_conversation` / `agent_message`, created by
 * Hibernate's `ddl-auto: update` like every other table here). Every exchange is kept for our
 * own analysis; within a session the last turns are replayed to the model from here.
 *
 * Reads are plain; writes are wrapped so a database hiccup while saving the exchange logs a
 * warning rather than failing a chat the user has already seen the answer to.
 */
@Component
class JpaAgentConversationMemory(
    private val repository: AgentConversationRepositoryAppAction
) : AgentConversationMemory {
    private val logger = LoggerFactory.getLogger(JpaAgentConversationMemory::class.java)

    override fun load(userEmail: String, conversationId: String, limit: Int): List<StoredAgentMessage> {
        if (limit <= 0) return emptyList()
        val conversation = repository.findOwned(conversationId, userEmail) ?: return emptyList()
        return repository.latestMessages(conversation.id!!, limit).map { it.toStored() }
    }

    override fun append(owner: AgentCaller, conversationId: String, messages: List<StoredAgentMessage>) {
        if (messages.isEmpty()) return
        val title = messages.firstOrNull { it.role == com.attendanceio.api.model.agent.AgentMessageRole.USER }?.content?.take(TITLE_LENGTH)
        try {
            repository.append(
                conversationId, owner, title,
                messages.map { m ->
                    DMAgentMessage().apply {
                        role = m.role
                        content = m.content
                        turnId = m.turnId
                        toolNames = m.toolNames?.takeIf { it.isNotEmpty() }?.joinToString(",")?.take(500)
                        latencyMs = m.latencyMs
                    }
                }
            )
        } catch (e: Exception) {
            logger.warn("agent=MEMORY_WRITE_FAILED conversationId={} reason={}", conversationId, e.message)
        }
    }

    private fun DMAgentMessage.toStored() = StoredAgentMessage(
        role = role,
        content = content,
        at = createdAt?.atZone(ZONE)?.toInstant() ?: java.time.Instant.EPOCH,
        turnId = turnId,
        toolNames = toolNames?.split(",")?.filter { it.isNotBlank() },
        latencyMs = latencyMs
    )

    private companion object {
        const val TITLE_LENGTH = 120
        val ZONE: ZoneId = ZoneId.systemDefault()
    }
}
