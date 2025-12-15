package com.attendanceio.api.model.search

data class SemesterInfoResponse(
    val id: String,
    val year: Int,
    val type: String
)

data class SemesterAttendanceResponse(
    val semester: SemesterInfoResponse,
    val subjects: List<SubjectAttendanceResponse>
)

