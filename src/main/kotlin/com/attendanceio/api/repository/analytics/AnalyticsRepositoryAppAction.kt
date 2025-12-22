package com.attendanceio.api.repository.analytics

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
}

data class AnalyticsStats(
    val totalStudents: Int,
    val totalSemesters: Int,
    val avgAttendance: Double,
    val above70: Int,
    val below60: Int
)

