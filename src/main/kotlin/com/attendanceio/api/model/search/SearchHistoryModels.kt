package com.attendanceio.api.model.search

data class SaveSearchHistoryRequest(val viewedStudentId: String)

data class SearchHistoryResponse(
    val id: Long,
    val viewedStudentId: String,
    val viewedStudentName: String,
    val viewedStudentRollNumber: String,
    val viewedStudentPictureUrl: String?,
    val createdAt: String?
)
