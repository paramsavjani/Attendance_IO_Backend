package com.attendanceio.api.repository.analytics

import com.attendanceio.api.model.analytics.AppAnalyticsResponse
import com.attendanceio.api.model.analytics.DailyCount
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

    fun getAppStats(): AppAnalyticsResponse {
        val r = analyticsRepository.getAppStats()
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
            attendanceLast15Days = r.attendanceLast15Days.map { DailyCount(it.first, it.second) },
            appOpensLast15Days = r.appOpensLast15Days.map { DailyCount(it.first, it.second) }
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

