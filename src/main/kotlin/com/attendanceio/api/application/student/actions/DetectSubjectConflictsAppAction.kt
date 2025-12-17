package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.TimetableConflict
import com.attendanceio.api.repository.schedule.SubjectScheduleRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.springframework.stereotype.Component

/**
 * Detects conflicts between selected subjects and existing timetable
 * without actually saving anything. Used for preview before enrollment.
 */
@Component
class DetectSubjectConflictsAppAction(
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val subjectScheduleRepositoryAppAction: SubjectScheduleRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction
) {
    
    /**
     * Detects conflicts for the given subject IDs.
     * Returns conflicts between:
     * 1. Selected subjects themselves (if multiple subjects have same schedule)
     * 2. Selected subjects and existing timetable
     */
    fun execute(student: DMStudent, subjectIds: List<Long>): List<TimetableConflict> {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            throw IllegalArgumentException("No active semester found")
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: throw IllegalArgumentException("Current semester ID is null")
        
        // Fetch all subjects for validation
        val allSubjects = if (subjectIds.isNotEmpty()) {
            subjectRepositoryAppAction.findAllById(subjectIds).associateBy { it.id!! }
        } else {
            emptyMap()
        }
        
        // Validate all subjects exist
        val missingSubjectIds = subjectIds.filter { it !in allSubjects.keys }
        if (missingSubjectIds.isNotEmpty()) {
            throw IllegalArgumentException("Subject(s) not found: ${missingSubjectIds.joinToString(", ")}")
        }
        
        // Fetch default schedules for selected subjects
        val defaultSchedules = subjectScheduleRepositoryAppAction.findBySubjectIds(subjectIds)
        
        if (defaultSchedules.isEmpty()) {
            return emptyList() // No schedules = no conflicts
        }
        
        // Get current timetable
        val existingTimetable = studentTimetableRepositoryAppAction
            .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
        
        // Build a map of existing slots: (dayId, slotId) -> existing entry
        val existingSlotMap = existingTimetable.associateBy { entry ->
            val dayId = entry.day?.id ?: return@associateBy null
            val slotId = entry.slot?.id ?: return@associateBy null
            Pair(dayId, slotId)
        }.filterKeys { it != null }.mapKeys { it.key!! }
        
        // Detect conflicts
        val conflicts = mutableListOf<TimetableConflict>()
        
        // Group schedules by (dayId, slotId) to find conflicts between selected subjects
        val scheduleGroups = defaultSchedules.groupBy { schedule ->
            val dayId = schedule.day?.id ?: return@groupBy null
            val slotId = schedule.slot?.id ?: return@groupBy null
            Pair(dayId, slotId)
        }.filterKeys { it != null }.mapKeys { it.key!! }
        
        // Check each group for conflicts
        scheduleGroups.forEach { (slotKey, schedules) ->
            val (dayId, slotId) = slotKey
            
            // Conflict 1: Multiple selected subjects want the same slot
            if (schedules.size > 1) {
                // Create conflicts between all pairs
                for (i in 0 until schedules.size) {
                    for (j in i + 1 until schedules.size) {
                        val schedule1 = schedules[i]
                        val schedule2 = schedules[j]
                        val subject1 = schedule1.subject
                        val subject2 = schedule2.subject
                        val day = schedule1.day
                        val slot = schedule1.slot
                        val subject1Id = subject1?.id
                        val subject2Id = subject2?.id
                        val slotStartTime = slot?.startTime
                        val slotEndTime = slot?.endTime
                        
                        // Skip if any required field is null
                        if (subject1 == null || subject2 == null || 
                            day == null || slot == null ||
                            subject1Id == null || subject2Id == null ||
                            slotStartTime == null || slotEndTime == null) {
                            continue
                        }
                        
                        conflicts.add(TimetableConflict(
                            dayId = dayId,
                            dayName = day.name,
                            slotId = slotId,
                            slotStartTime = slotStartTime,
                            slotEndTime = slotEndTime,
                            existingSubjectId = subject1Id,
                            existingSubjectCode = subject1.code,
                            existingSubjectName = subject1.name,
                            newSubjectId = subject2Id,
                            newSubjectCode = subject2.code,
                            newSubjectName = subject2.name
                        ))
                    }
                }
            }
            
            // Conflict 2: Selected subject conflicts with existing timetable
            val existingEntry = existingSlotMap[slotKey]
            if (existingEntry != null) {
                val existingSubject = existingEntry.subject
                val day = schedules[0].day
                val slot = schedules[0].slot
                val existingSubjectId = existingSubject?.id
                val slotStartTime = slot?.startTime
                val slotEndTime = slot?.endTime
                
                if (existingSubject != null && day != null && slot != null &&
                    existingSubjectId != null && slotStartTime != null && slotEndTime != null) {
                    // Create conflict for each selected subject that wants this slot
                    schedules.forEach { schedule ->
                        val newSubject = schedule.subject
                        val newSubjectId = newSubject?.id
                        
                        if (newSubject != null && newSubjectId != null) {
                            conflicts.add(TimetableConflict(
                                dayId = dayId,
                                dayName = day.name,
                                slotId = slotId,
                                slotStartTime = slotStartTime,
                                slotEndTime = slotEndTime,
                                existingSubjectId = existingSubjectId,
                                existingSubjectCode = existingSubject.code,
                                existingSubjectName = existingSubject.name,
                                newSubjectId = newSubjectId,
                                newSubjectCode = newSubject.code,
                                newSubjectName = newSubject.name
                            ))
                        }
                    }
                }
            }
        }
        
        return conflicts
    }
}

