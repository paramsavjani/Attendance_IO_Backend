package com.attendanceio.api.model.attendance

data class MarkedAttendanceSummary(
    val studentId: Long,
    val presentLectures: Int,
    val markedLectures: Int
)
