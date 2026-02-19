package com.attendanceio.api.repository.analytics

import com.attendanceio.api.model.analytics.AppAnalyticsResponse
import com.attendanceio.api.model.analytics.DailyCount
import com.attendanceio.api.model.analytics.HourCount
import org.springframework.stereotype.Component

@Component
class AnalyticsRepositoryAppAction(
    private val analyticsRepository: AnalyticsRepository
) {
    fun getAnalyticsStats(semesterId: Long?): AnalyticsStats {
        val result = analyticsRepository.getAnalyticsStats(semesterId)
        return AnalyticsStats(
            totalStudents = (result[0] as Number).toInt(),
            totalSemesters = (result[1] as Number).toInt(),
            avgAttendance = (result[2] as? Number)?.toDouble() ?: 0.0,
            above70 = (result[3] as Number).toInt(),
            below60 = (result[4] as Number).toInt()
        )
    }
    
    fun getAttendancePercentages(semesterId: Long?): List<Double> {
        return analyticsRepository.getAttendancePercentages(semesterId)
    }
    
    fun getTotalSubjects(semesterId: Long?): Int {
        return analyticsRepository.getTotalSubjects(semesterId)
    }

    fun getAppStats(range: String? = "30"): AppAnalyticsResponse {
        val allTime = range == "all"
        val r = analyticsRepository.getAppStats(allTime = allTime)
        return AppAnalyticsResponse(
            totalUsers = r.totalUsers,
            totalAttendance = r.totalAttendance,
            totalEvents = r.totalEvents,
            recentLogins = r.recentLogins,
            eventsByType = r.eventsByType,
            notificationsEnabled = r.notificationsEnabled,
            notificationsDisabled = r.notificationsDisabled,
            totalEnrollments = r.totalEnrollments,
            studentsWithSubjects = r.studentsWithSubjects,
            uniqueSubjects = r.uniqueSubjects,
            attendanceLast30Days = r.attendanceLast30Days.map { DailyCount(it.first, it.second) },
            appOpensLast30Days = r.appOpensLast30Days.map { DailyCount(it.first, it.second) },
            attendanceByHour = r.attendanceByHour.map { HourCount(it.first, it.second) }
        )
    }
}

data class AnalyticsStats(
    val totalStudents: Int,
    val totalSemesters: Int,
    val avgAttendance: Double,
    val above70: Int,
    val below60: Int
)

