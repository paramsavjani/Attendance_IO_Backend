package com.attendanceio.api.model.attendance

/** Overall attendance as it stood at the end of each recent week, and the student's streak. */
data class AttendanceProgressResponse(
    val trend: List<TrendPoint>,
    val streak: StreakInfo
)

/** [date] is the last day counted (a Sunday, or today for the newest point). */
data class TrendPoint(
    val date: String,
    val present: Int,
    val total: Int
)

/**
 * Days in a row, among days with a class marked, where every marked class was present.
 * Cancelled classes don't count either way, and days with nothing marked are skipped.
 */
data class StreakInfo(
    val current: Int,
    val best: Int
)
