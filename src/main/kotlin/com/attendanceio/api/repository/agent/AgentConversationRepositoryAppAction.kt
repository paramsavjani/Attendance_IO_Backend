package com.attendanceio.api.repository.agent

import com.attendanceio.api.model.agent.DMAgentConversation
import com.attendanceio.api.model.agent.DMAgentMessage
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AgentConversationRepositoryAppAction(
    private val conversationRepository: AgentConversationRepository,
    private val messageRepository: AgentMessageRepository
) {
    /** Scoped to the owner: a conversation id from another user's thread reads as not found. */
    @Transactional(readOnly = true)
    fun findOwned(conversationId: String, userEmail: String): DMAgentConversation? =
        conversationRepository.findByConversationIdAndUserEmail(conversationId, userEmail.trim().lowercase())

    /** The last [limit] messages in chronological order. */
    @Transactional(readOnly = true)
    fun latestMessages(conversationDbId: Long, limit: Int): List<DMAgentMessage> =
        messageRepository.findByConversationIdOrderByIdDesc(conversationDbId, PageRequest.of(0, limit)).reversed()

    /** Creates the thread on first use, appends the messages, bumps the counters — one transaction. */
    @Transactional
    fun append(conversationId: String, userEmail: String, title: String?, messages: List<DMAgentMessage>) {
        val email = userEmail.trim().lowercase()
        val conversation = conversationRepository.findByConversationIdAndUserEmail(conversationId, email)
            ?: conversationRepository.save(
                DMAgentConversation().apply {
                    this.conversationId = conversationId
                    this.userEmail = email
                    this.title = title
                }
            )
        messages.forEach { it.conversation = conversation }
        messageRepository.saveAll(messages)
        conversation.messageCount += messages.size
        if (conversation.title == null) conversation.title = title
        conversationRepository.save(conversation)
    }
}
