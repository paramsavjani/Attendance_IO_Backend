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
    /** Last 30 days: date -> count (frontend can slice to 5/15/30) */
    val attendanceLast30Days: List<DailyCount>,
    /** Last 30 days: date -> count (frontend can slice to 5/15/30) */
    val appOpensLast30Days: List<DailyCount>,
    /** Attendance count by hour of day (0-23), e.g. for "avg attendance by 24 hours" */
    val attendanceByHour: List<HourCount>,
    /** Total attendance by day of week (0=Sunday .. 6=Saturday), all-time from 5 Jan 2026 */
    val attendanceByDayOfWeek: List<DayOfWeekCount>
)

data class DayOfWeekCount(
    val dayOfWeek: Int,
    val count: Int
)

data class DailyCount(
    val date: String,
    val count: Int
)

data class HourCount(
    val hour: Int,
    val count: Int
)
