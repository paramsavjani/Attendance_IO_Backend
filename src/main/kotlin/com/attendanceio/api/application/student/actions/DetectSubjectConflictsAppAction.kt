package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.TimetableConflict
import com.attendanceio.api.repository.schedule.SubjectScheduleRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class DetectSubjectConflictsAppAction(
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val subjectScheduleRepositoryAppAction: SubjectScheduleRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction
) {
    fun execute(student: DMStudent, subjectIds: List<Long>): List<TimetableConflict> {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")

        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) throw IllegalArgumentException("No active semester found")
        val currentSemesterId = activeSemesters.first().id ?: throw IllegalArgumentException("Current semester ID is null")

        val allSubjects = if (subjectIds.isNotEmpty()) {
            subjectRepositoryAppAction.findAllById(subjectIds).associateBy { it.id!! }
        } else emptyMap()

        val missingSubjectIds = subjectIds.filter { it !in allSubjects.keys }
        if (missingSubjectIds.isNotEmpty()) {
            throw IllegalArgumentException("Subject(s) not found: ${missingSubjectIds.joinToString(", ")}")
        }

        val previousSubjectIds = studentSubjectRepositoryAppAction.findByStudentId(studentId)
            .filter { it.subject?.semester?.id == currentSemesterId }
            .mapNotNull { it.subject?.id }.toSet()

        val newSubjectIds = subjectIds.toSet()
        val removedSubjectIds = previousSubjectIds - newSubjectIds
        val addedSubjectIds = newSubjectIds - previousSubjectIds

        if (addedSubjectIds.isEmpty()) return emptyList()

        val defaultSchedules = subjectScheduleRepositoryAppAction.findBySubjectIds(addedSubjectIds.toList())
        if (defaultSchedules.isEmpty()) return emptyList()

        val existingTimetable = studentTimetableRepositoryAppAction
            .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
            .filter { entry -> entry.subject?.id?.let { it !in removedSubjectIds } != false }

        val existingSlotMap = existingTimetable.associateBy { entry ->
            val dayId = entry.day?.id ?: return@associateBy null
            val slotId = entry.slot?.id ?: return@associateBy null
            Pair(dayId, slotId)
        }.filterKeys { it != null }.mapKeys { it.key!! }

        val conflicts = mutableListOf<TimetableConflict>()

        val scheduleGroups = defaultSchedules.groupBy { schedule ->
            val dayId = schedule.day?.id ?: return@groupBy null
            val slotId = schedule.slot?.id ?: return@groupBy null
            Pair(dayId, slotId)
        }.filterKeys { it != null }.mapKeys { it.key!! }

        scheduleGroups.forEach { (slotKey, schedules) ->
            val (dayId, slotId) = slotKey

            if (schedules.size > 1) {
                for (i in 0 until schedules.size) {
                    for (j in i + 1 until schedules.size) {
                        val subject1 = schedules[i].subject
                        val subject2 = schedules[j].subject
                        val day = schedules[i].day
                        val slot = schedules[i].slot
                        val subject1Id = subject1?.id
                        val subject2Id = subject2?.id
                        val slotStartTime = slot?.startTime
                        val slotEndTime = slot?.endTime
                        if (subject1 == null || subject2 == null || day == null || slot == null ||
                            subject1Id == null || subject2Id == null || slotStartTime == null || slotEndTime == null) continue
                        conflicts.add(TimetableConflict(
                            dayId = dayId, dayName = day.name, slotId = slotId,
                            slotStartTime = slotStartTime, slotEndTime = slotEndTime,
                            existingSubjectId = subject1Id, existingSubjectCode = subject1.code,
                            existingSubjectName = subject1.name, newSubjectId = subject2Id,
                            newSubjectCode = subject2.code, newSubjectName = subject2.name
                        ))
                    }
                }
            }

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
                    schedules.forEach { schedule ->
                        val newSubject = schedule.subject
                        val newSubjectId = newSubject?.id
                        if (newSubject != null && newSubjectId != null && newSubjectId != existingSubjectId) {
                            conflicts.add(TimetableConflict(
                                dayId = dayId, dayName = day.name, slotId = slotId,
                                slotStartTime = slotStartTime, slotEndTime = slotEndTime,
                                existingSubjectId = existingSubjectId, existingSubjectCode = existingSubject.code,
                                existingSubjectName = existingSubject.name, newSubjectId = newSubjectId,
                                newSubjectCode = newSubject.code, newSubjectName = newSubject.name
                            ))
                        }
                    }
                }
            }
        }

        return conflicts
    }
}
