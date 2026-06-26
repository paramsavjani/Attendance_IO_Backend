package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.model.timetable.*
import com.attendanceio.api.repository.schedule.SubjectScheduleRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SyncTimetableWithSubjectsAppAction(
    private val subjectScheduleRepositoryAppAction: SubjectScheduleRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction
) {
    @Transactional
    fun execute(
        student: DMStudent,
        semester: DMSemester,
        previousSubjectIds: Set<Long>,
        newSubjectIds: Set<Long>,
        allSubjects: Map<Long, DMSubject>,
        conflictResolutions: Map<String, String>? = null
    ): SubjectEnrollmentSyncResult {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        val semesterId = semester.id ?: throw IllegalArgumentException("Semester ID is null")

        val removedSubjectIds = previousSubjectIds - newSubjectIds
        val addedSubjectIds = newSubjectIds - previousSubjectIds

        val removedSubjects = removedSubjectIds.mapNotNull { id -> allSubjects[id]?.let { SubjectInfo(id, it.code, it.name) } }
        val addedSubjects = addedSubjectIds.mapNotNull { id -> allSubjects[id]?.let { SubjectInfo(id, it.code, it.name) } }

        val slotsRemoved = if (removedSubjectIds.isNotEmpty()) {
            studentTimetableRepositoryAppAction.deleteAllByStudentIdAndSemesterIdAndSubjectIds(
                studentId, semesterId, removedSubjectIds.toList()
            )
        } else 0

        if (addedSubjectIds.isEmpty()) {
            return SubjectEnrollmentSyncResult(
                success = true,
                hasConflicts = false,
                conflicts = emptyList(),
                addedSubjects = addedSubjects,
                removedSubjects = removedSubjects,
                subjectsWithConflicts = emptyList(),
                timetableSlotsAdded = 0,
                timetableSlotsRemoved = slotsRemoved,
                message = if (slotsRemoved > 0) "Removed $slotsRemoved timetable slot(s) for unenrolled subjects"
                          else "Subject enrollment updated successfully"
            )
        }

        val defaultSchedules = subjectScheduleRepositoryAppAction.findBySubjectIds(addedSubjectIds.toList())

        val existingTimetable = studentTimetableRepositoryAppAction
            .findByStudentIdAndSemesterIdWithDetails(studentId, semesterId)

        val existingSlotMap = existingTimetable.associateBy { entry ->
            val dayId = entry.day?.id ?: return@associateBy null
            val slotId = entry.slot?.id ?: return@associateBy null
            Pair(dayId, slotId)
        }.filterKeys { it != null }.mapKeys { it.key!! }

        val scheduleGroups = defaultSchedules.groupBy { schedule ->
            val dayId = schedule.day?.id ?: return@groupBy null
            val slotId = schedule.slot?.id ?: return@groupBy null
            Pair(dayId, slotId)
        }.filterKeys { it != null }.mapKeys { it.key!! }

        val conflicts = mutableListOf<TimetableConflict>()
        val subjectsWithConflictsSet = mutableSetOf<Long>()
        val nonConflictingEntries = mutableListOf<DMStudentTimetable>()

        scheduleGroups.forEach { (slotKey, schedules) ->
            val (dayId, slotId) = slotKey
            val conflictKey = "${dayId}-${slotId}"
            val existingEntry = existingSlotMap[slotKey]
            val resolvedSubjectId = conflictResolutions?.get(conflictKey)
            val day = schedules[0].day ?: return@forEach
            val slot = schedules[0].slot ?: return@forEach

            if (resolvedSubjectId != null) {
                if (existingEntry != null) {
                    if (existingEntry.subject?.id?.toString() == resolvedSubjectId) return@forEach
                    val chosenSubject = schedules.find { it.subject?.id?.toString() == resolvedSubjectId }?.subject
                    if (chosenSubject != null) {
                        existingEntry.subject = chosenSubject
                        studentTimetableRepositoryAppAction.save(existingEntry)
                    }
                } else {
                    val chosenSubject = schedules.find { it.subject?.id?.toString() == resolvedSubjectId }?.subject
                    if (chosenSubject != null) {
                        nonConflictingEntries.add(DMStudentTimetable().apply {
                            this.student = student; this.semester = semester
                            this.subject = chosenSubject; this.day = day; this.slot = slot
                        })
                    }
                }
                return@forEach
            }

            if (existingEntry != null) {
                val existingSubject = existingEntry.subject
                val existingSubjectId = existingSubject?.id
                val slotStartTime = slot.startTime
                val slotEndTime = slot.endTime
                if (existingSubject != null && existingSubjectId != null && slotStartTime != null && slotEndTime != null) {
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
                            subjectsWithConflictsSet.add(newSubjectId)
                        }
                    }
                }
            } else if (schedules.size > 1) {
                for (i in 0 until schedules.size) {
                    for (j in i + 1 until schedules.size) {
                        val subject1 = schedules[i].subject
                        val subject2 = schedules[j].subject
                        val subject1Id = subject1?.id
                        val subject2Id = subject2?.id
                        val slotStartTime = slot.startTime
                        val slotEndTime = slot.endTime
                        if (subject1 == null || subject2 == null || subject1Id == null || subject2Id == null ||
                            slotStartTime == null || slotEndTime == null) continue
                        conflicts.add(TimetableConflict(
                            dayId = dayId, dayName = day.name, slotId = slotId,
                            slotStartTime = slotStartTime, slotEndTime = slotEndTime,
                            existingSubjectId = subject1Id, existingSubjectCode = subject1.code,
                            existingSubjectName = subject1.name, newSubjectId = subject2Id,
                            newSubjectCode = subject2.code, newSubjectName = subject2.name
                        ))
                        subjectsWithConflictsSet.add(subject1Id)
                        subjectsWithConflictsSet.add(subject2Id)
                    }
                }
            } else {
                val subject = schedules[0].subject ?: return@forEach
                nonConflictingEntries.add(DMStudentTimetable().apply {
                    this.student = student; this.semester = semester
                    this.subject = subject; this.day = day; this.slot = slot
                })
            }
        }

        if (nonConflictingEntries.isNotEmpty()) {
            studentTimetableRepositoryAppAction.saveAll(nonConflictingEntries)
        }

        val subjectsWithConflicts = subjectsWithConflictsSet.mapNotNull { id ->
            allSubjects[id]?.let { SubjectInfo(id, it.code, it.name) }
        }

        return if (conflicts.isNotEmpty()) {
            SubjectEnrollmentSyncResult(
                success = false,
                hasConflicts = true,
                conflicts = conflicts,
                addedSubjects = addedSubjects,
                removedSubjects = removedSubjects,
                subjectsWithConflicts = subjectsWithConflicts,
                timetableSlotsAdded = nonConflictingEntries.size,
                timetableSlotsRemoved = slotsRemoved,
                message = "Timetable conflicts detected. ${conflicts.size} slot(s) could not be added. Please resolve conflicts manually."
            )
        } else {
            SubjectEnrollmentSyncResult(
                success = true,
                hasConflicts = false,
                conflicts = emptyList(),
                addedSubjects = addedSubjects,
                removedSubjects = removedSubjects,
                subjectsWithConflicts = emptyList(),
                timetableSlotsAdded = nonConflictingEntries.size,
                timetableSlotsRemoved = slotsRemoved,
                message = "Subject enrollment updated successfully. ${nonConflictingEntries.size} timetable slot(s) added."
            )
        }
    }
}
