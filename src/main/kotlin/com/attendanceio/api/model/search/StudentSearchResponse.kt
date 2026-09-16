package com.attendanceio.api.model.search

data class StudentSearchResponse(
    val id: String,
    val name: String,
    val rollNumber: String,
    val email: String? = null,
    val pictureUrl: String? = null,
    /** Student-marked (app) lectures in the active semester, excluding cancelled ones. */
    val markedLectures: Int = 0,
    val presentLectures: Int = 0,
    /** present / marked as a percentage; null when nothing has been marked. */
    val attendancePercentage: Double? = null
)
