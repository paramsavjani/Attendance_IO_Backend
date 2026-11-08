package com.attendanceio.api.application.poll.actions

import com.attendanceio.api.application.poll.PollAccess
import com.attendanceio.api.application.poll.PollException
import com.attendanceio.api.application.poll.PollViewAssembler
import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.model.poll.PollResponse
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.poll.PollRepositoryAppAction
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class VotePollAppAction(
    private val pollRepositoryAppAction: PollRepositoryAppAction,
    private val pollAccess: PollAccess,
    private val pollViewAssembler: PollViewAssembler
) {
    /** Sets the student's ballot, replacing any earlier one. Allowed until the poll ends. */
    fun vote(pollId: Long, student: DMStudent, optionIds: List<Long>?): PollResponse {
        val studentId = student.id ?: throw PollException.badRequest("Invalid student")
        val poll = pollAccess.visiblePoll(pollId, studentId)
        requireOpen(poll)

        val chosen = optionIds.orEmpty().distinct()
        if (chosen.isEmpty()) throw PollException.badRequest("Pick an option")
        if (!poll.allowMultiple && chosen.size > 1) throw PollException.badRequest("This poll takes one answer")

        val options = pollRepositoryAppAction.optionsFor(listOf(pollId))[pollId].orEmpty()
        val picked = options.filter { it.id in chosen }
        if (picked.size != chosen.size) throw PollException.badRequest("That option is not part of this poll")

        try {
            pollRepositoryAppAction.replaceBallot(poll, student, picked)
        } catch (e: DataIntegrityViolationException) {
            // Two taps raced each other; one ballot won, which is what the student asked for.
        }
        return pollViewAssembler.toResponse(poll, studentId)
    }

    fun clear(pollId: Long, student: DMStudent): PollResponse {
        val studentId = student.id ?: throw PollException.badRequest("Invalid student")
        val poll = pollAccess.visiblePoll(pollId, studentId)
        requireOpen(poll)
        pollRepositoryAppAction.clearBallot(pollId, studentId)
        return pollViewAssembler.toResponse(poll, studentId)
    }

    private fun requireOpen(poll: DMPoll) {
        if (!poll.isOpen()) throw PollException.conflict("This poll has ended")
    }
}
