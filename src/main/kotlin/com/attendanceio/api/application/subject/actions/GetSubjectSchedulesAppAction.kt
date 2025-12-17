package com.attendanceio.api.application.subject.actions

import com.attendanceio.api.model.schedule.SubjectScheduleResponse
import com.attendanceio.api.repository.schedule.SubjectScheduleRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetSubjectSchedulesAppAction(
    private val subjectScheduleRepositoryAppAction: SubjectScheduleRepositoryAppAction
) {
    /**
     * Get all default schedules for the given subject IDs.
     * Used for conflict detection before enrollment.
     */
    fun execute(subjectIds: List<Long>): List<SubjectScheduleResponse> {
        if (subjectIds.isEmpty()) return emptyList()
        
        val schedules = subjectScheduleRepositoryAppAction.findBySubjectIds(subjectIds)
        
        return schedules.mapNotNull { schedule ->
            val subject = schedule.subject ?: return@mapNotNull null
            val day = schedule.day ?: return@mapNotNull null
            val slot = schedule.slot ?: return@mapNotNull null
            
            SubjectScheduleResponse(
                subjectId = subject.id!!,
                subjectCode = subject.code,
                subjectName = subject.name,
                dayId = day.id!!.toInt(),
                dayName = day.name,
                slotId = slot.id!!.toInt(),
                slotStartTime = slot.startTime?.toString() ?: "",
                slotEndTime = slot.endTime?.toString() ?: ""
            )
        }
    }
}

