package com.attendanceio.api.repository.agent

import com.attendanceio.api.model.agent.DMAgentMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AgentMessageRepository : JpaRepository<DMAgentMessage, Long> {
    /** Newest first so a page of `n` is the last `n` messages; callers reverse it for replay. */
    fun findByConversationIdOrderByIdDesc(conversationId: Long, pageable: Pageable): List<DMAgentMessage>
}
