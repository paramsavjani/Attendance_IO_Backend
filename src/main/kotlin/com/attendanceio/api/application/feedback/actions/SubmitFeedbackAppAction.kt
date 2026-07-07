package com.attendanceio.api.application.feedback.actions

import com.attendanceio.api.model.feedback.DMFeedback
import com.attendanceio.api.model.feedback.FeedbackRequest
import com.attendanceio.api.model.feedback.FeedbackType
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.feedback.FeedbackRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class SubmitFeedbackAppAction(
    private val feedbackRepositoryAppAction: FeedbackRepositoryAppAction
) {
    fun execute(student: DMStudent, request: FeedbackRequest): DMFeedback {
        require(request.title.isNotBlank() && request.description.isNotBlank()) {
            "Title and description are required"
        }
        require(request.title.length <= 100) { "Title must be 100 characters or less" }
        require(request.description.length <= 500) { "Description must be 500 characters or less" }

        val feedbackType = try {
            FeedbackType.valueOf(request.type.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid feedback type. Must be one of: bug, feedback, suggestion")
        }

        val feedback = DMFeedback().apply {
            this.student = student
            this.type = feedbackType
            this.title = request.title.trim()
            this.description = request.description.trim()
        }
        return feedbackRepositoryAppAction.save(feedback)
    }
}
