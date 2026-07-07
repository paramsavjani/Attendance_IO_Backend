package com.attendanceio.api.model.analytics

data class AnalyticsStats(
    val totalStudents: Int,
    val totalSemesters: Int,
    val avgAttendance: Double,
    val above70: Int,
    val below60: Int
)
