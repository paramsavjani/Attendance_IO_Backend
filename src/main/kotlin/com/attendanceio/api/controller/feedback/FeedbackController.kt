package com.attendanceio.api.controller.feedback

import com.attendanceio.api.application.feedback.actions.GetMyFeedbackAppAction
import com.attendanceio.api.application.feedback.actions.SubmitFeedbackAppAction
import com.attendanceio.api.model.feedback.FeedbackRequest
import com.attendanceio.api.model.feedback.FeedbackResponse
import com.attendanceio.api.model.feedback.MyFeedbackResponse
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.feedback.FeedbackRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/feedback")
class FeedbackController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val submitFeedbackAppAction: SubmitFeedbackAppAction,
    private val getMyFeedbackAppAction: GetMyFeedbackAppAction,
    private val feedbackRepositoryAppAction: FeedbackRepositoryAppAction
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

    /** The caller's own threads and replies. Never takes a student id from the request. */
    @GetMapping("/mine")
    fun myFeedback(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<MyFeedbackResponse> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).build()
        val id = student.id ?: return ResponseEntity.status(404).build()
        return ResponseEntity.ok(getMyFeedbackAppAction.execute(id))
    }

    /** Clears the unread badge for one thread. 404 for a thread that is not the caller's. */
    @PostMapping("/{feedbackId}/seen")
    fun markSeen(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable feedbackId: Long
    ): ResponseEntity<Void> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).build()
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        val thread = feedbackRepositoryAppAction.findOwnedBy(feedbackId, studentId)
            ?: return ResponseEntity.status(404).build()
        feedbackRepositoryAppAction.markSeen(thread)
        return ResponseEntity.noContent().build()
    }

    private fun resolveCaller(oauth2User: OAuth2User?): DMStudent? {
        val email = oauth2User?.getAttribute<String>("email") ?: return null
        return studentRepositoryAppAction.findByEmail(email)
    }
}
