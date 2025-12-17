package com.attendanceio.api.repository.schedule

import com.attendanceio.api.model.schedule.DMSubjectSchedule
import org.springframework.stereotype.Component

@Component
class SubjectScheduleRepositoryAppAction(
    private val subjectScheduleRepository: SubjectScheduleRepository
) {
    /**
     * Find all schedule entries for a given subject
     */
    fun findBySubjectId(subjectId: Long): List<DMSubjectSchedule> {
        return subjectScheduleRepository.findBySubjectId(subjectId)
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

