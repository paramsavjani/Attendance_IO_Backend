package com.attendanceio.api.repository.agent

import com.attendanceio.api.model.agent.DMAgentConversation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AgentConversationRepository : JpaRepository<DMAgentConversation, Long> {
    fun findByConversationIdAndUserEmail(conversationId: String, userEmail: String): DMAgentConversation?
}
