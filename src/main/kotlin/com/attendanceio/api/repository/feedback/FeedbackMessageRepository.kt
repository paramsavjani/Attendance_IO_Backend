package com.attendanceio.api.repository.feedback

import com.attendanceio.api.model.feedback.DMFeedbackMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FeedbackMessageRepository : JpaRepository<DMFeedbackMessage, Long> {
    fun findByFeedbackIdInOrderByCreatedAtAsc(feedbackIds: Collection<Long>): List<DMFeedbackMessage>
}
