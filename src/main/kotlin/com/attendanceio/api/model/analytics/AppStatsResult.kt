package com.attendanceio.api.model.analytics

data class AppStatsResult(
    val totalUsers: Int,
    val totalAttendance: Int,
    val totalEvents: Int,
    val recentLogins: Int,
    val eventsByType: Map<String, Int>,
    val notificationsEnabled: Int,
    val notificationsDisabled: Int,
    val totalEnrollments: Int,
    val studentsWithSubjects: Int,
    val uniqueSubjects: Int,
    val attendanceLast30Days: List<Pair<String, Int>>,
    val appOpensLast30Days: List<Pair<String, Int>>,
    val attendanceByHour: List<Pair<Int, Int>>,
    val attendanceByDayOfWeek: List<Pair<Int, Int>>
)
