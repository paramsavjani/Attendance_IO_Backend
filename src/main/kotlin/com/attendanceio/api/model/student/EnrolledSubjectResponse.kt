package com.attendanceio.api.model.student

data class EnrolledSubjectResponse(
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String
)

data class EnrolledSubjectsResponse(
    val subjects: List<EnrolledSubjectResponse>
)

data class SaveEnrolledSubjectsRequest(
    val subjectIds: List<String>
)

