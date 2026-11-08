package com.attendanceio.api.controller.poll

import com.attendanceio.api.application.poll.PollException
import com.attendanceio.api.application.poll.actions.CreatePollAppAction
import com.attendanceio.api.application.poll.actions.GetPollFeedAppAction
import com.attendanceio.api.application.poll.actions.ManagePollAppAction
import com.attendanceio.api.application.poll.actions.VotePollAppAction
import com.attendanceio.api.model.poll.CastVoteRequest
import com.attendanceio.api.model.poll.CreatePollRequest
import com.attendanceio.api.model.poll.ReportPollRequest
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.util.DemoUserUtil
import org.slf4j.LoggerFactory
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

/**
 * Class polls: a student asks one subject's class a question. Every route acts as the signed-in
 * student; ids in the path are checked against their enrollment, never trusted.
 */
@RestController
@RequestMapping("/api/polls")
class PollController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val getPollFeedAppAction: GetPollFeedAppAction,
    private val createPollAppAction: CreatePollAppAction,
    private val votePollAppAction: VotePollAppAction,
    private val managePollAppAction: ManagePollAppAction
) {
    private val log = LoggerFactory.getLogger(PollController::class.java)

    @GetMapping
    fun feed(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Any> {
        if (oauth2User == null) return unauthorized()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.ok(getPollFeedAppAction.execute(DemoUserUtil.DEMO_STUDENT_ID, canWrite = false))
        }
        val studentId = resolveCaller(oauth2User)?.id ?: return unauthorized()
        return ResponseEntity.ok(getPollFeedAppAction.execute(studentId, canWrite = true))
    }

    @PostMapping
    fun create(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: CreatePollRequest
    ): ResponseEntity<Any> = asStudent(oauth2User, "create") { student ->
        createPollAppAction.execute(student, request)
    }

    @PutMapping("/{pollId}/vote")
    fun vote(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long,
        @RequestBody request: CastVoteRequest
    ): ResponseEntity<Any> = asStudent(oauth2User, "vote") { student ->
        votePollAppAction.vote(pollId, student, request.optionIds)
    }

    @DeleteMapping("/{pollId}/vote")
    fun clearVote(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long
    ): ResponseEntity<Any> = asStudent(oauth2User, "clear vote") { student ->
        votePollAppAction.clear(pollId, student)
    }

    @PostMapping("/{pollId}/close")
    fun close(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long
    ): ResponseEntity<Any> = asStudent(oauth2User, "close") { student ->
        managePollAppAction.close(pollId, student.id!!)
    }

    @DeleteMapping("/{pollId}")
    fun delete(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long
    ): ResponseEntity<Any> = asStudent(oauth2User, "delete") { student ->
        managePollAppAction.delete(pollId, student.id!!)
        mapOf("deleted" to true)
    }

    @PostMapping("/{pollId}/report")
    fun report(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable pollId: Long,
        @RequestBody request: ReportPollRequest
    ): ResponseEntity<Any> = asStudent(oauth2User, "report") { student ->
        managePollAppAction.report(pollId, student, request.reason)
    }

    /** Runs a write as the signed-in student, mapping rule violations to their HTTP status. */
    private fun asStudent(oauth2User: OAuth2User?, action: String, block: (DMStudent) -> Any): ResponseEntity<Any> {
        if (oauth2User == null) return unauthorized()
        if (DemoUserUtil.isDemoUser(oauth2User)) return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        val student = resolveCaller(oauth2User)?.takeIf { it.id != null } ?: return unauthorized()
        return try {
            ResponseEntity.ok(block(student))
        } catch (e: PollException) {
            ResponseEntity.status(e.status).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("Poll {} failed for student {}", action, student.id, e)
            ResponseEntity.status(500).body(mapOf("error" to "Something went wrong. Please try again."))
        }
    }

    private fun resolveCaller(oauth2User: OAuth2User): DMStudent? {
        val email = oauth2User.getAttribute<String>("email") ?: return null
        return studentRepositoryAppAction.findByEmail(email)
    }

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
}
