package com.attendanceio.api.model.feedback

data class FeedbackRequest(
    val type: String,
    val title: String,
    val description: String
)

