package com.attendanceio.api.controller.event

import com.attendanceio.api.model.event.DMUserEvent
import com.attendanceio.api.repository.event.UserEventRepositoryAppAction
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
    private val userEventRepositoryAppAction: UserEventRepositoryAppAction
) {
    private val log = LoggerFactory.getLogger(EventController::class.java)

    data class TrackEventRequest(
        val eventType: String, // e.g., "app_open", "page_view", etc.
        val metadata: Map<String, Any>? = null
    )

    @PostMapping("/track")
    fun trackEvent(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: TrackEventRequest
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)

        if (student != null) {
            // Save event to database
            val event = DMUserEvent().apply {
                this.student = student
                this.eventType = request.eventType
                this.metadata = request.metadata
            }
            val savedEvent = userEventRepositoryAppAction.save(event)
            
            // Log the event for analytics
            log.info(
                "Event tracked: type={}, studentId={}, email={}, eventId={}, metadata={}",
                request.eventType,
                student.id,
                email,
                savedEvent.id,
                request.metadata ?: emptyMap<String, Any>()
            )
            
            return ResponseEntity.ok(
                mapOf<String, Any>(
                    "message" to "Event tracked successfully",
                    "eventType" to request.eventType,
                    "studentId" to (student.id ?: ""),
                    "eventId" to (savedEvent.id ?: ""),
                    "timestamp" to System.currentTimeMillis()
                )
            )
        } else {
            log.warn("Event tracking failed: student not found. email={}", email)
            return ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        }
    }
}
