package com.attendanceio.api.model.agent

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Whether a student would pay for the assistant, asked when they run out of today's messages. One
 * row per account: the offer counters move every time it is shown, and the latest answer wins, so
 * "how many said yes" is a plain count with no de-duplication.
 */
@Entity
@Table(
    name = "agent_subscription_interest",
    indexes = [
        Index(name = "idx_agent_subscription_interest_email", columnList = "user_email", unique = true),
        Index(name = "idx_agent_subscription_interest_response", columnList = "response")
    ]
)
class DMAgentSubscriptionInterest : BaseEntity() {
    @Column(name = "user_email", nullable = false, length = 255, unique = true)
    var userEmail: String = ""

    @Column(name = "student_id")
    var studentId: Long? = null

    /** Denormalised roll number so analysis queries need no join. */
    @Column(name = "roll_number", length = 20)
    var rollNumber: String? = null

    /** Times this account hit the daily limit and was shown the offer. */
    @Column(name = "offer_count", nullable = false)
    var offerCount: Int = 0

    @Column(name = "last_offered_at")
    var lastOfferedAt: LocalDateTime? = null

    /** The price the offer named, in rupees per month, so a later price test stays comparable. */
    @Column(name = "price_rupees", nullable = false)
    var priceRupees: Int = 0

    /** YES or NO; null until they answer. */
    @Column(name = "response", length = 3)
    var response: String? = null

    @Column(name = "responded_at")
    var respondedAt: LocalDateTime? = null
}
