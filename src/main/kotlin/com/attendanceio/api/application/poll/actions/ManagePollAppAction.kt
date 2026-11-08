package com.attendanceio.api.application.poll.actions

import com.attendanceio.api.application.poll.PollAccess
import com.attendanceio.api.application.poll.PollException
import com.attendanceio.api.application.poll.PollRules
import com.attendanceio.api.application.poll.PollViewAssembler
import com.attendanceio.api.model.poll.DMPollReport
import com.attendanceio.api.model.poll.PollReportReason
import com.attendanceio.api.model.poll.PollResponse
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.poll.PollRepositoryAppAction
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.time.Instant

/** What a poll's creator and its classmates can do to a poll beyond voting. */
@Component
class ManagePollAppAction(
    private val pollRepositoryAppAction: PollRepositoryAppAction,
    private val pollAccess: PollAccess,
    private val pollViewAssembler: PollViewAssembler
) {
    /** Ends voting now. Results stay visible to the class. */
    fun close(pollId: Long, studentId: Long): PollResponse {
        val poll = pollAccess.ownPoll(pollId, studentId)
        if (poll.closedAt == null && poll.isOpen()) {
            poll.closedAt = Instant.now()
            pollRepositoryAppAction.save(poll)
        }
        return pollViewAssembler.toResponse(poll, studentId)
    }

    fun delete(pollId: Long, studentId: Long) {
        pollRepositoryAppAction.delete(pollAccess.ownPoll(pollId, studentId))
    }

    /** Idempotent: a second report from the same student changes nothing. */
    fun report(pollId: Long, student: DMStudent, reason: String?): PollResponse {
        val studentId = student.id ?: throw PollException.badRequest("Invalid student")
        val poll = pollAccess.visiblePoll(pollId, studentId)
        if (poll.creator?.id == studentId) throw PollException.badRequest("You can't report your own poll")

        val parsedReason = reason?.let { value ->
            PollReportReason.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw PollException.badRequest("Unknown report reason")
        } ?: PollReportReason.OTHER

        if (!pollRepositoryAppAction.hasReported(pollId, studentId)) {
            val reports = try {
                pollRepositoryAppAction.addReport(DMPollReport().apply {
                    this.poll = poll
                    this.student = student
                    this.reason = parsedReason
                })
            } catch (e: DataIntegrityViolationException) {
                0
            }
            if (reports >= PollRules.REPORTS_TO_HIDE && !poll.hidden) {
                poll.hidden = true
                pollRepositoryAppAction.save(poll)
            }
        }
        return pollViewAssembler.toResponse(poll, studentId)
    }
}
