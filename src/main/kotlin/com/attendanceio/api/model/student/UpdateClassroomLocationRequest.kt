package com.attendanceio.api.model.student

data class UpdateClassroomLocationRequest(
    val subjectId: String,
    val classroomLocation: String?
)
