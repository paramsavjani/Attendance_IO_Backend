package com.attendanceio.api.model.search

data class StudentSearchResponse(
    val id: String,
    val name: String,
    val rollNumber: String,
    val email: String? = null,
    val pictureUrl: String? = null,
    /** Active-semester lectures counted: the institute's official total, or (without one) the student's own marks. */
    val markedLectures: Int = 0,
    val presentLectures: Int = 0,
    /** present / marked as a percentage; null when nothing has been marked. */
    val attendancePercentage: Double? = null,
    /** INSTITUTE or STUDENT: where the numbers above come from; null when there are none. */
    val attendanceSource: String? = null
)
