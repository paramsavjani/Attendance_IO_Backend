package com.attendanceio.api.model.subject

data class SubjectResponse(
    val id: String,
    val code: String,
    val name: String,
    val lecturePlace: String? = null
)

