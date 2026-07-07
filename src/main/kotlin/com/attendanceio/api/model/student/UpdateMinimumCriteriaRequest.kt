package com.attendanceio.api.model.student

data class UpdateMinimumCriteriaRequest(
    val subjectId: String,
    val minimumCriteria: Int?
)
