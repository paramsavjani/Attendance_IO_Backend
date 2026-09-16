package com.attendanceio.api.model.search

data class StudentAttendanceResponse(
    val studentId: String,
    val studentName: String,
    val rollNumber: String,
    val studentPictureUrl: String? = null,
    val source: String,
    val semesters: List<SemesterAttendanceResponse>
)

