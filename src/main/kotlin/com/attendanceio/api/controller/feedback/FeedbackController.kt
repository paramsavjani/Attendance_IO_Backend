package com.attendanceio.api.controller.feedback

import com.attendanceio.api.model.feedback.DMFeedback
import com.attendanceio.api.model.feedback.FeedbackRequest
import com.attendanceio.api.model.feedback.FeedbackResponse
import com.attendanceio.api.model.feedback.FeedbackType
import com.attendanceio.api.repository.feedback.FeedbackRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/feedback")
class FeedbackController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val feedbackRepositoryAppAction: FeedbackRepositoryAppAction
) {
    @PostMapping
    fun submitFeedback(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: FeedbackRequest
    ): ResponseEntity<FeedbackResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()

        return try {
            // Validate request
            if (request.title.isBlank() || request.description.isBlank()) {
                return ResponseEntity.status(400).body(
                    FeedbackResponse(0, "Title and description are required")
                )
            }

            if (request.title.length > 100) {
                return ResponseEntity.status(400).body(
                    FeedbackResponse(0, "Title must be 100 characters or less")
                )
            }

            if (request.description.length > 500) {
                return ResponseEntity.status(400).body(
                    FeedbackResponse(0, "Description must be 500 characters or less")
                )
            }

            // Parse feedback type
            val feedbackType = try {
                FeedbackType.valueOf(request.type.uppercase())
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.status(400).body(
                    FeedbackResponse(0, "Invalid feedback type. Must be one of: bug, feedback, suggestion")
                )
            }

            // Create and save feedback
            val feedback = DMFeedback().apply {
                this.student = student
                this.type = feedbackType
                this.title = request.title.trim()
                this.description = request.description.trim()
            }

            val savedFeedback = feedbackRepositoryAppAction.save(feedback)

            ResponseEntity.ok(
                FeedbackResponse(
                    id = savedFeedback.id ?: 0,
                    message = "Thank you for your feedback!"
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                FeedbackResponse(0, "Internal server error: ${e.message}")
            )
        }
    }
}

