package com.attendanceio.api.model.timetable

import java.time.LocalTime

data class SubjectInfo(val subjectId: Long, val subjectCode: String, val subjectName: String)

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
