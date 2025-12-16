package com.attendanceio.api.model.attendance

import java.time.LocalDate

data class MarkAttendanceRequest(
    val subjectId: String,
    val lectureDate: String, // ISO format: "yyyy-MM-dd"
    val status: String // "present" or "absent"
)

data class MarkAttendanceResponse(
    val message: String,
    val attendanceId: Long?,
    val subjectId: String,
    val lectureDate: String,
    val status: String
)

