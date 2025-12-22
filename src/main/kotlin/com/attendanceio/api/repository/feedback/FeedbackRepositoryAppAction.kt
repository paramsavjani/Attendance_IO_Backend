package com.attendanceio.api.repository.feedback

import com.attendanceio.api.model.feedback.DMFeedback
import org.springframework.stereotype.Component

@Component
class FeedbackRepositoryAppAction(
    private val feedbackRepository: FeedbackRepository
) {
    fun save(feedback: DMFeedback): DMFeedback {
        return feedbackRepository.save(feedback)
    }
}

