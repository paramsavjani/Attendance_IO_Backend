package com.attendanceio.api.application.poll

import com.attendanceio.api.model.poll.DMPoll
import com.attendanceio.api.repository.poll.PollRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import org.springframework.stereotype.Component

/**
 * Who may see and act on which polls. A poll belongs to one subject, and only students enrolled
 * in that subject this semester can see it, vote on it or report it.
 */
@Component
class PollAccess(
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val pollRepositoryAppAction: PollRepositoryAppAction
) {
    /** Ids of the subjects the student takes in the active semester. */
    fun currentSubjectIds(studentId: Long): Set<Long> {
        val semesterId = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()?.id ?: return emptySet()
        return studentSubjectRepositoryAppAction.findByStudentId(studentId)
            .mapNotNull { it.subject }
            .filter { it.semester?.id == semesterId }
            .mapNotNull { it.id }
            .toSet()
    }

    fun isEnrolled(studentId: Long, subjectId: Long): Boolean = subjectId in currentSubjectIds(studentId)

    /**
     * The poll, if the student may see it. Anything else reads as "not found" so ids of other
     * classes' polls reveal nothing.
     */
    fun visiblePoll(pollId: Long, studentId: Long): DMPoll {
        val poll = pollRepositoryAppAction.findById(pollId)?.takeIf { !it.isDeleted } ?: throw PollException.notFound()
        val subjectId = poll.subject?.id ?: throw PollException.notFound()
        val isCreator = poll.creator?.id == studentId
        if (!isCreator && (poll.hidden || !isEnrolled(studentId, subjectId))) throw PollException.notFound()
        return poll
    }

    fun ownPoll(pollId: Long, studentId: Long): DMPoll {
        val poll = visiblePoll(pollId, studentId)
        if (poll.creator?.id != studentId) throw PollException.forbidden("Only the person who asked can do that")
        return poll
    }
}
