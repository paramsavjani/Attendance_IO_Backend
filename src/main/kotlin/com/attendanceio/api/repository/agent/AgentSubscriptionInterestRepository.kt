package com.attendanceio.api.repository.agent

import com.attendanceio.api.model.agent.DMAgentSubscriptionInterest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AgentSubscriptionInterestRepository : JpaRepository<DMAgentSubscriptionInterest, Long> {
    fun findByUserEmail(userEmail: String): DMAgentSubscriptionInterest?
}
