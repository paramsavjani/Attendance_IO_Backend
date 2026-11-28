package com.attendanceio.api.model.attendance

data class MarkedAttendanceSummary(
    val studentId: Long,
    val presentLectures: Int,
    val markedLectures: Int,
    /** INSTITUTE when the counts are the official record, STUDENT when they're what the student marked. */
    val source: String = "STUDENT"
)
