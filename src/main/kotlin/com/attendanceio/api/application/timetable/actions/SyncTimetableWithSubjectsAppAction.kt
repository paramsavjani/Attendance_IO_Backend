package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.model.timetable.*
import com.attendanceio.api.repository.schedule.SubjectScheduleRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Synchronizes a student's timetable with their subject selection.
 * 
 * This action ensures timetable consistency by:
 * 1. Removing timetable slots for unenrolled subjects
 * 2. Adding default timetable slots for newly enrolled subjects
 * 3. Detecting and reporting conflicts (never auto-resolving them)
 * 
 * If conflicts are detected, the operation is partially completed:
 * - Removed subjects' slots ARE deleted
 * - Non-conflicting new slots ARE added
 * - Conflicting slots are NOT added (returned to frontend for resolution)
 */
@Component
class SyncTimetableWithSubjectsAppAction(
    private val subjectScheduleRepositoryAppAction: SubjectScheduleRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction
) {
    
    /**
     * Synchronizes the student's timetable based on subject changes.
     * 
     * @param student The student whose timetable to sync
     * @param semester The current semester
     * @param previousSubjectIds Previously enrolled subject IDs
     * @param newSubjectIds Newly selected subject IDs
     * @param allSubjects Map of all subjects by ID (for subject details)
     * @return Sync result with conflicts (if any) and operation details
     */
    @Transactional
    fun execute(
        student: DMStudent,
        semester: DMSemester,
        previousSubjectIds: Set<Long>,
        newSubjectIds: Set<Long>,
        allSubjects: Map<Long, DMSubject>
    ): SubjectEnrollmentSyncResult {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        val semesterId = semester.id ?: throw IllegalArgumentException("Semester ID is null")
        
        // Step 1: Compute changes
        val removedSubjectIds = previousSubjectIds - newSubjectIds
        val addedSubjectIds = newSubjectIds - previousSubjectIds
        
        // Build subject info lists for response
        val removedSubjects = removedSubjectIds.mapNotNull { id ->
            allSubjects[id]?.let { 
                SubjectInfo(id, it.code, it.name) 
            }
        }
        val addedSubjects = addedSubjectIds.mapNotNull { id ->
            allSubjects[id]?.let { 
                SubjectInfo(id, it.code, it.name) 
            }
        }
        
        // Step 2: Handle removed subjects - delete their timetable slots
        val slotsRemoved = if (removedSubjectIds.isNotEmpty()) {
            studentTimetableRepositoryAppAction.deleteAllByStudentIdAndSemesterIdAndSubjectIds(
                studentId, semesterId, removedSubjectIds.toList()
            )
        } else 0
        
        // If no subjects were added, we're done
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
                message = if (slotsRemoved > 0) 
                    "Removed $slotsRemoved timetable slot(s) for unenrolled subjects" 
                else 
                    "Subject enrollment updated successfully"
            )
        }
        
        // Step 3: Fetch default schedules for added subjects
        val defaultSchedules = subjectScheduleRepositoryAppAction.findBySubjectIds(addedSubjectIds.toList())
        
        // Step 4: Get current timetable to check for conflicts
        val existingTimetable = studentTimetableRepositoryAppAction
            .findByStudentIdAndSemesterIdWithDetails(studentId, semesterId)
        
        // Build a map of existing slots: (dayId, slotId) -> existing entry
        val existingSlotMap = existingTimetable.associateBy { entry ->
            val dayId = entry.day?.id ?: return@associateBy null
            val slotId = entry.slot?.id ?: return@associateBy null
            Pair(dayId, slotId)
        }.filterKeys { it != null }.mapKeys { it.key!! }
        
        // Step 5: Check for conflicts and separate conflicting vs non-conflicting slots
        val conflicts = mutableListOf<TimetableConflict>()
        val subjectsWithConflictsSet = mutableSetOf<Long>()
        val nonConflictingEntries = mutableListOf<DMStudentTimetable>()
        
        for (schedule in defaultSchedules) {
            val subject = schedule.subject ?: continue
            val day = schedule.day ?: continue
            val slot = schedule.slot ?: continue
            val dayId = day.id ?: continue
            val slotId = slot.id ?: continue
            
            val slotKey = Pair(dayId, slotId)
            val existingEntry = existingSlotMap[slotKey]
            
            if (existingEntry != null) {
                // Conflict detected!
                val existingSubject = existingEntry.subject ?: continue
                
                conflicts.add(TimetableConflict(
                    dayId = dayId,
                    dayName = day.name,
                    slotId = slotId,
                    slotStartTime = slot.startTime ?: continue,
                    slotEndTime = slot.endTime ?: continue,
                    existingSubjectId = existingSubject.id ?: continue,
                    existingSubjectCode = existingSubject.code,
                    existingSubjectName = existingSubject.name,
                    newSubjectId = subject.id ?: continue,
                    newSubjectCode = subject.code,
                    newSubjectName = subject.name
                ))
                subjectsWithConflictsSet.add(subject.id!!)
            } else {
                // No conflict - prepare entry for insertion
                nonConflictingEntries.add(DMStudentTimetable().apply {
                    this.student = student
                    this.semester = semester
                    this.subject = subject
                    this.day = day
                    this.slot = slot
                })
            }
        }
        
        // Step 6: Save non-conflicting entries
        if (nonConflictingEntries.isNotEmpty()) {
            studentTimetableRepositoryAppAction.saveAll(nonConflictingEntries)
        }
        
        // Build subjects with conflicts info
        val subjectsWithConflicts = subjectsWithConflictsSet.mapNotNull { id ->
            allSubjects[id]?.let { SubjectInfo(id, it.code, it.name) }
        }
        
        // Step 7: Return result
        return if (conflicts.isNotEmpty()) {
            SubjectEnrollmentSyncResult(
                success = false, // Partial success - conflicts exist
                hasConflicts = true,
                conflicts = conflicts,
                addedSubjects = addedSubjects,
                removedSubjects = removedSubjects,
                subjectsWithConflicts = subjectsWithConflicts,
                timetableSlotsAdded = nonConflictingEntries.size,
                timetableSlotsRemoved = slotsRemoved,
                message = "Timetable conflicts detected. ${conflicts.size} slot(s) could not be added. " +
                        "Please resolve conflicts manually."
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
                message = "Subject enrollment updated successfully. " +
                        "${nonConflictingEntries.size} timetable slot(s) added."
            )
        }
    }
}

