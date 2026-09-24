package com.attendanceio.api.repository.schedule

import com.attendanceio.api.model.schedule.DMSubjectSchedule
import org.springframework.stereotype.Component

@Component
class SubjectScheduleRepositoryAppAction(
    private val subjectScheduleRepository: SubjectScheduleRepository
) {
    /**
     * Find all schedule entries for a given subject, with day and slot already loaded.
     *
     * Goes through the fetch-joined query rather than the plain derived one: callers read
     * `schedule.day` and `schedule.slot` after this returns, and the entities are detached by then.
     * On a request thread open-session-in-view hid that, but the assistant runs its tools on a
     * reactive thread with no session, where a lazy slot threw "Could not initialize proxy" and lost
     * the student their whole answer.
     */
    fun findBySubjectId(subjectId: Long): List<DMSubjectSchedule> {
        return subjectScheduleRepository.findBySubjectIdIn(listOf(subjectId))
    }
    
    /**
     * Find all schedule entries for multiple subjects in a single query.
     * Optimized with eager fetching to avoid N+1 queries.
     */
    fun findBySubjectIds(subjectIds: List<Long>): List<DMSubjectSchedule> {
        if (subjectIds.isEmpty()) return emptyList()
        return subjectScheduleRepository.findBySubjectIdIn(subjectIds)
    }
}

