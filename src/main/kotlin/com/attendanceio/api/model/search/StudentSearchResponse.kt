package com.attendanceio.api.model.search

data class StudentSearchResponse(
    val id: String,
    val name: String,
    val rollNumber: String,
    val email: String? = null,
    val pictureUrl: String? = null
)

