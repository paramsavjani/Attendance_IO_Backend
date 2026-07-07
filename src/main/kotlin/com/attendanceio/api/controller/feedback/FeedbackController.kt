package com.attendanceio.api.controller.feedback

import com.attendanceio.api.application.feedback.actions.SubmitFeedbackAppAction
import com.attendanceio.api.model.feedback.FeedbackRequest
import com.attendanceio.api.model.feedback.FeedbackResponse
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
    private val submitFeedbackAppAction: SubmitFeedbackAppAction
) {
    @PostMapping
    fun submitFeedback(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: FeedbackRequest
    ): ResponseEntity<FeedbackResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()

        return try {
            val saved = submitFeedbackAppAction.execute(student, request)
            ResponseEntity.ok(FeedbackResponse(id = saved.id ?: 0, message = "Thank you for your feedback!"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(FeedbackResponse(0, e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(FeedbackResponse(0, "Internal server error: ${e.message}"))
        }
    }
}
