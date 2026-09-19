package com.attendanceio.api.repository.agent

import com.attendanceio.api.model.agent.DMAgentMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import org.springframework.stereotype.Repository

@Repository
interface AgentMessageRepository : JpaRepository<DMAgentMessage, Long> {
    /** Newest first so a page of `n` is the last `n` messages; callers reverse it for replay. */
    fun findByConversationIdOrderByIdDesc(conversationId: Long, pageable: Pageable): List<DMAgentMessage>

    /** Messages a user sent since [since] across all their threads — the daily quota counter. */
    @Query(
        "SELECT COUNT(m) FROM DMAgentMessage m WHERE m.conversation.userEmail = :email AND m.role = com.attendanceio.api.model.agent.AgentMessageRole.USER AND m.createdAt >= :since"
    )
    fun countUserMessagesSince(@Param("email") email: String, @Param("since") since: LocalDateTime): Long
}
