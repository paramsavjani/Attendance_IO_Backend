package com.attendanceio.api.model.analytics

data class AppAnalyticsResponse(
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
    val attendanceLast15Days: List<DailyCount>,
    val appOpensLast15Days: List<DailyCount>
)

data class DailyCount(
    val date: String,
    val count: Int
)
