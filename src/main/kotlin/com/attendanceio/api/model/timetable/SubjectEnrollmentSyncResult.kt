package com.attendanceio.api.model.timetable

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
