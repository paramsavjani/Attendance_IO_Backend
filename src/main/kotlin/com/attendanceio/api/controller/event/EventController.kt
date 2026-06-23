package com.attendanceio.api.controller.event

import com.attendanceio.api.application.event.actions.TrackEventAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/event")
class EventController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val trackEventAppAction: TrackEventAppAction
) {
    private val log = LoggerFactory.getLogger(EventController::class.java)

    data class TrackEventRequest(
        val eventType: String,
        val metadata: Map<String, Any>? = null
    )

    @PostMapping("/track")
    fun trackEvent(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: TrackEventRequest
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: run {
                log.warn("Event tracking failed: student not found. email={}", email)
                return ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
            }

        val saved = trackEventAppAction.execute(student, request.eventType, request.metadata)
        log.info(
            "Event tracked: type={}, studentId={}, email={}, eventId={}, metadata={}",
            request.eventType, student.id, email, saved.id, request.metadata ?: emptyMap<String, Any>()
        )
        return ResponseEntity.ok(
            mapOf<String, Any>(
                "message" to "Event tracked successfully",
                "eventType" to request.eventType,
                "studentId" to (student.id ?: ""),
                "eventId" to (saved.id ?: ""),
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
}
