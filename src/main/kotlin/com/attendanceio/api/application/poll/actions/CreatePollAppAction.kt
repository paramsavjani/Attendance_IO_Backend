package com.attendanceio.api.application.poll.actions

import com.attendanceio.api.application.poll.PollAccess
import com.attendanceio.api.application.poll.PollException
import com.attendanceio.api.application.poll.PollNotifier
import com.attendanceio.api.application.poll.PollRules
import com.attendanceio.api.application.poll.PollViewAssembler
import com.attendanceio.api.model.poll.CreatePollRequest
import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.model.poll.PollResponse
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.poll.PollRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

@Component
class CreatePollAppAction(
    private val pollRepositoryAppAction: PollRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val pollAccess: PollAccess,
    private val pollViewAssembler: PollViewAssembler,
    private val pollNotifier: PollNotifier
) {
    fun execute(student: DMStudent, request: CreatePollRequest): PollResponse {
        val studentId = student.id ?: throw PollException.badRequest("Invalid student")
        val subjectId = request.subjectId ?: throw PollException.badRequest("Pick a subject")

        val question = normalize(request.question)
        if (question.length < PollRules.QUESTION_MIN) throw PollException.badRequest("Write a question")
        if (question.length > PollRules.QUESTION_MAX) {
            throw PollException.badRequest("Keep the question under ${PollRules.QUESTION_MAX} characters")
        }

        val options = request.options.orEmpty().map(::normalize).filter { it.isNotEmpty() }
        if (options.size < PollRules.MIN_OPTIONS) throw PollException.badRequest("Add at least ${PollRules.MIN_OPTIONS} options")
        if (options.size > PollRules.MAX_OPTIONS) throw PollException.badRequest("Use at most ${PollRules.MAX_OPTIONS} options")
        if (options.any { it.length > PollRules.OPTION_MAX }) {
            throw PollException.badRequest("Keep each option under ${PollRules.OPTION_MAX} characters")
        }
        if (options.map { it.lowercase() }.toSet().size != options.size) throw PollException.badRequest("Options must be different")

        val hours = request.durationHours ?: 24
        if (hours !in PollRules.DURATION_HOURS) throw PollException.badRequest("Pick how long the poll stays open")

        val enrollment = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
        val subject = enrollment?.subject
        if (subject == null || !pollAccess.isEnrolled(studentId, subjectId)) {
            throw PollException.forbidden("You can only ask polls in subjects you take")
        }

        if (pollRepositoryAppAction.countHiddenBy(studentId) > PollRules.HIDDEN_POLLS_ALLOWED) {
            throw PollException.forbidden(BLOCKED_MESSAGE)
        }

        val createdToday = pollRepositoryAppAction.countCreatedSince(studentId, LocalDateTime.now().minusDays(1))
        if (createdToday >= PollRules.DAILY_CREATE_LIMIT) {
            throw PollException.tooMany("You can ask ${PollRules.DAILY_CREATE_LIMIT} polls a day. Try again tomorrow.")
        }

        val poll = pollRepositoryAppAction.create(
            DMPoll().apply {
                this.subject = subject
                this.creator = student
                this.creatorName = student.name
                this.question = question
                this.allowMultiple = request.allowMultiple == true
                this.anonymous = request.anonymous == true
                this.closesAt = Instant.now().plus(Duration.ofHours(hours.toLong()))
            },
            options
        )
        pollNotifier.notifyNewPoll(poll)
        return pollViewAssembler.toResponse(poll, studentId)
    }

    companion object {
        const val BLOCKED_MESSAGE =
            "You can't ask polls anymore because several of your polls were hidden after classmates reported them."
    }

    /** Trims and collapses runs of whitespace so "  Yes  " and "Yes" count as the same option. */
    private fun normalize(text: String?): String = text.orEmpty().trim().replace(Regex("\\s+"), " ")
}
