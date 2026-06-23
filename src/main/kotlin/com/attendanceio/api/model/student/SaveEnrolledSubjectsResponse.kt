package com.attendanceio.api.model.student

import com.attendanceio.api.model.timetable.SubjectInfo
import com.attendanceio.api.model.timetable.TimetableConflict

data class SaveEnrolledSubjectsResponse(
    val success: Boolean,
    val message: String,
    val subjectIds: List<String>,
    val count: Int,
    val hasConflicts: Boolean,
    val conflicts: List<TimetableConflict>,
    val addedSubjects: List<SubjectInfo>,
    val removedSubjects: List<SubjectInfo>,
    val subjectsWithConflicts: List<SubjectInfo>,
    val timetableSlotsAdded: Int,
    val timetableSlotsRemoved: Int
)
