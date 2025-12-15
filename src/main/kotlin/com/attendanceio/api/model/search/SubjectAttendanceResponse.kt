package com.attendanceio.api.model.search

data class SubjectAttendanceResponse(
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val present: Int,
    val absent: Int,
    val leave: Int,
    val total: Int
)

