package com.attendanceio.api.repository.feedback

import com.attendanceio.api.model.feedback.DMFeedback
import com.attendanceio.api.model.feedback.DMFeedbackMessage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class FeedbackRepositoryAppAction(
    private val feedbackRepository: FeedbackRepository,
    private val feedbackMessageRepository: FeedbackMessageRepository
) {
    fun save(feedback: DMFeedback): DMFeedback {
        return feedbackRepository.save(feedback)
    }

    @Transactional(readOnly = true)
    fun findMine(studentId: Long): List<DMFeedback> =
        feedbackRepository.findByStudentIdOrderByCreatedAtDesc(studentId)

    @Transactional(readOnly = true)
    fun messagesFor(feedbackIds: Collection<Long>): Map<Long, List<DMFeedbackMessage>> {
        if (feedbackIds.isEmpty()) return emptyMap()
        return feedbackMessageRepository.findByFeedbackIdInOrderByCreatedAtAsc(feedbackIds)
            .groupBy { it.feedback?.id ?: 0L }
    }

    @Transactional(readOnly = true)
    fun findOwnedBy(feedbackId: Long, studentId: Long): DMFeedback? =
        feedbackRepository.findById(feedbackId)
            .orElse(null)
            ?.takeIf { it.student?.id == studentId }

    /** Stamps the thread as seen. Idempotent — opening it twice is not an error. */
    @Transactional
    fun markSeen(feedback: DMFeedback) {
        feedback.studentSeenAt = LocalDateTime.now()
        feedbackRepository.save(feedback)
    }
}

