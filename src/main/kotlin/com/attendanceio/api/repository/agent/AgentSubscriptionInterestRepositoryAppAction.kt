package com.attendanceio.api.repository.agent

import com.attendanceio.api.application.agent.AgentCaller
import com.attendanceio.api.model.agent.DMAgentSubscriptionInterest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class AgentSubscriptionInterestRepositoryAppAction(
    private val repository: AgentSubscriptionInterestRepository
) {
    @Transactional(readOnly = true)
    fun find(email: String): DMAgentSubscriptionInterest? = repository.findByUserEmail(email.trim().lowercase())

    /** Counts one more showing of the offer, creating the account's row on first use. */
    @Transactional
    fun recordOffer(caller: AgentCaller, priceRupees: Int, at: LocalDateTime) {
        val row = rowFor(caller)
        row.offerCount += 1
        row.lastOfferedAt = at
        row.priceRupees = priceRupees
        repository.save(row)
    }

    /** Stores YES/NO; a later answer replaces an earlier one. */
    @Transactional
    fun recordAnswer(caller: AgentCaller, interested: Boolean, priceRupees: Int, at: LocalDateTime) {
        val row = rowFor(caller)
        row.response = if (interested) YES else NO
        row.respondedAt = at
        if (row.priceRupees == 0) row.priceRupees = priceRupees
        repository.save(row)
    }

    private fun rowFor(caller: AgentCaller): DMAgentSubscriptionInterest {
        val email = caller.email.trim().lowercase()
        return repository.findByUserEmail(email) ?: DMAgentSubscriptionInterest().apply {
            userEmail = email
            studentId = caller.studentId
            rollNumber = caller.rollNumber
        }
    }

    companion object {
        const val YES = "YES"
        const val NO = "NO"
    }
}
