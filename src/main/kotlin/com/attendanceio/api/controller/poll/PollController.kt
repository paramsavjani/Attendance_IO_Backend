package com.attendanceio.api.controller.poll

import com.attendanceio.api.model.poll.CastVoteRequest
import com.attendanceio.api.model.poll.CreatePollRequest
import com.attendanceio.api.model.poll.PollNotificationPreferencesDto
import com.attendanceio.api.model.poll.PollResponse
import com.attendanceio.api.model.poll.ReportPollRequest
import com.attendanceio.api.model.poll.ReportPollResponse
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.service.PollService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/polls")
class PollController(
    private val pollService: PollService,
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    private fun resolveCaller(oauth2User: OAuth2User?): DMStudent? {
        val email = oauth2User?.getAttribute<String>("email") ?: return null
        return studentRepositoryAppAction.findByEmail(email)
    }

    /**
     * Create a new poll for a subject. Caller must be enrolled in the subject.
     */
    @PostMapping
    fun createPoll(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: CreatePollRequest
    ): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        return try {
            val response = pollService.createPoll(student, request)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(mapOf("error" to (e.message ?: "Action not allowed")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to create poll: ${e.message}"))
        }
    }

    /**
     * Get all polls for a specific subject (ordered newest first).
     */
    @GetMapping("/subject/{subjectId}")
    fun getPollsBySubject(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable subjectId: Long
    ): ResponseEntity<List<PollResponse>> {
        val student = resolveCaller(oauth2User)
        val polls = pollService.getPollsForSubject(subjectId, student?.id)
        return ResponseEntity.ok(polls)
    }

    /**
     * Get details and current vote counts for a single poll.
     */
    @GetMapping("/{pollId}")
    fun getPollDetail(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long
    ): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User)
        return try {
            val poll = pollService.getPollDetail(pollId, student?.id)
            ResponseEntity.ok(poll)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(404).body(mapOf("error" to (e.message ?: "Poll not found")))
        }
    }

    /**
     * Cast or update a vote on a poll.
     */
    @PostMapping("/{pollId}/vote")
    fun castVote(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long,
        @RequestBody request: CastVoteRequest
    ): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        return try {
            val updated = pollService.castVote(pollId, student, request)
            ResponseEntity.ok(updated)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid vote request")))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Voting error")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to record vote: ${e.message}"))
        }
    }

    /**
     * Close a poll early (Creator only).
     */
    @PostMapping("/{pollId}/close")
    fun closePoll(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long
    ): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        val studentId = student.id ?: return ResponseEntity.status(401).body(mapOf("error" to "Invalid student"))
        return try {
            val closed = pollService.closePoll(pollId, studentId)
            ResponseEntity.ok(closed)
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(mapOf("error" to (e.message ?: "Forbidden")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to close poll: ${e.message}"))
        }
    }

    /**
     * Delete a poll (Creator only).
     */
    @DeleteMapping("/{pollId}")
    fun deletePoll(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long
    ): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        val studentId = student.id ?: return ResponseEntity.status(401).body(mapOf("error" to "Invalid student"))
        return try {
            pollService.deletePoll(pollId, studentId)
            ResponseEntity.ok(mapOf("message" to "Poll deleted successfully"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(mapOf("error" to (e.message ?: "Forbidden")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to delete poll: ${e.message}"))
        }
    }

    /**
     * Report a poll for spam or offensive content.
     */
    @PostMapping("/{pollId}/report")
    fun reportPoll(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long,
        @RequestBody request: ReportPollRequest
    ): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        return try {
            val response: ReportPollResponse = pollService.reportPoll(pollId, student, request)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to report poll: ${e.message}"))
        }
    }

    /**
     * Get polls created by the logged-in student.
     */
    @GetMapping("/my-polls")
    fun getMyPolls(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        val studentId = student.id ?: return ResponseEntity.status(401).body(mapOf("error" to "Invalid student"))
        val polls = pollService.getMyPolls(studentId)
        return ResponseEntity.ok(polls)
    }
}

@RestController
@RequestMapping("/api/user/poll-notification-preferences")
class PollNotificationPreferencesController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    private fun resolveCaller(oauth2User: OAuth2User?): DMStudent? {
        val email = oauth2User?.getAttribute<String>("email") ?: return null
        return studentRepositoryAppAction.findByEmail(email)
    }

    @GetMapping
    fun getPreferences(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        return ResponseEntity.ok(
            PollNotificationPreferencesDto(
                notifyNewPolls = student.pollNotifyNewPolls,
                notifyPollResults = student.pollNotifyResults
            )
        )
    }

    @PutMapping
    fun updatePreferences(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody prefs: PollNotificationPreferencesDto
    ): ResponseEntity<Any> {
        val student = resolveCaller(oauth2User) ?: return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        student.pollNotifyNewPolls = prefs.notifyNewPolls
        student.pollNotifyResults = prefs.notifyPollResults
        studentRepositoryAppAction.save(student)
        return ResponseEntity.ok(mapOf("message" to "Poll notification preferences updated successfully"))
    }
}
