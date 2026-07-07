package com.attendanceio.api.model.student

data class SaveBaselineAttendanceRequest(
    val subjectId: String,
    val cutoffDate: String,
    val totalClasses: Int,
    val presentClasses: Int
)

data class BaselineAttendanceResponse(
    val subjectId: String,
    val cutoffDate: String?,
    val totalClasses: Int?,
    val presentClasses: Int?
)
