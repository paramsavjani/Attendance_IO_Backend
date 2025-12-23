package com.attendanceio.api.model.attendance

import java.time.LocalDate

data class TodayAttendanceRecord(
    val attendanceId: Long?,
    val subjectId: String,
    val lectureDate: String, // ISO format
    val status: String // "present" or "absent"
)

data class SubjectStatsResponse(
    val subjectId: String,
    val present: Int,
    val absent: Int,
    val total: Int,
    val totalUntilEndDate: Int = 0,
    val percentage: Double = 0.0,
    val classesNeeded: Int = 0,
    val bunkableClasses: Int = 0
)

data class MyAttendanceResponse(
    val subjectStats: List<SubjectStatsResponse>,
    val todayAttendance: List<TodayAttendanceRecord>
)

