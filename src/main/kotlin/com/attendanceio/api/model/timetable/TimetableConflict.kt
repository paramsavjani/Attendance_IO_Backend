package com.attendanceio.api.model.timetable

import java.time.LocalTime

/**
 * Represents a conflict when adding a new subject to the timetable.
 * A conflict occurs when the new subject's default schedule overlaps
 * with an existing entry in the student's timetable.
 */
data class TimetableConflict(
    val dayId: Short,
    val dayName: String,
    val slotId: Short,
    val slotStartTime: LocalTime,
    val slotEndTime: LocalTime,
    val existingSubjectId: Long,
    val existingSubjectCode: String,
    val existingSubjectName: String,
    val newSubjectId: Long,
    val newSubjectCode: String,
    val newSubjectName: String
)

/**
 * Response returned when subject enrollment results in timetable conflicts.
 */
data class SubjectEnrollmentConflictResponse(
    val hasConflicts: Boolean,
    val conflicts: List<TimetableConflict>,
    val message: String,
    val addedSubjects: List<SubjectInfo>,
    val removedSubjects: List<SubjectInfo>,
    val subjectsWithConflicts: List<SubjectInfo>
)

/**
 * Basic subject information for conflict response
 */
data class SubjectInfo(
    val subjectId: Long,
    val subjectCode: String,
    val subjectName: String
)

/**
 * Result of the subject enrollment sync operation
 */
data class SubjectEnrollmentSyncResult(
    val success: Boolean,
    val hasConflicts: Boolean,
    val conflicts: List<TimetableConflict>,
    val addedSubjects: List<SubjectInfo>,
    val removedSubjects: List<SubjectInfo>,
    val subjectsWithConflicts: List<SubjectInfo>,
    val timetableSlotsAdded: Int,
    val timetableSlotsRemoved: Int,
    val message: String
)

