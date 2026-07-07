package com.attendanceio.api.application.analytics.actions

import com.attendanceio.api.model.analytics.AnalyticsResponse
import com.attendanceio.api.model.analytics.DistributionItem
import com.attendanceio.api.model.analytics.RangeItem
import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.repository.analytics.AnalyticsRepositoryAppAction
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

@Component
class CalculateAnalyticsAppAction(
    private val analyticsRepositoryAppAction: AnalyticsRepositoryAppAction
) {
    @Cacheable(value = ["analytics"], key = "#semester?.id ?: 'all'", unless = "#result.totalStudents == 0")
    fun calculateForSemester(semester: DMSemester?): AnalyticsResponse {
        val semesterId = semester?.id
        val stats = analyticsRepositoryAppAction.getAnalyticsStats(semesterId)
        val totalSubjects = analyticsRepositoryAppAction.getTotalSubjects(semesterId)
        val percentages = analyticsRepositoryAppAction.getAttendancePercentages(semesterId)

        if (percentages.isEmpty()) {
            return AnalyticsResponse(
                totalStudents = stats.totalStudents,
                totalSubjects = totalSubjects,
                averageAttendance = 0.0,
                above70 = 0,
                below60 = 0,
                distribution = emptyList(),
                ranges = emptyList()
            )
        }

        val totalWithData = percentages.size
        fun pct(count: Int) = if (totalWithData > 0) ((count.toDouble() / totalWithData) * 100).roundToLong().toInt() else 0

        val above70Count = percentages.count { it >= 70 }
        val between60And70Count = percentages.count { it in 60.0..<70.0 }
        val below60Count = percentages.count { it < 60 }

        val distribution = listOf(
            DistributionItem("Above 70%", pct(above70Count), "hsl(var(--success))"),
            DistributionItem("60-75%", pct(between60And70Count), "hsl(var(--warning))"),
            DistributionItem("Below 60%", pct(below60Count), "hsl(var(--destructive))")
        )

        val rangeCounts = mutableMapOf("0-20%" to 0, "20-40%" to 0, "40-60%" to 0, "60-70%" to 0, "70-80%" to 0, "80-90%" to 0, "90-100%" to 0)
        percentages.forEach { p ->
            val key = when {
                p < 20 -> "0-20%"
                p < 40 -> "20-40%"
                p < 60 -> "40-60%"
                p < 70 -> "60-70%"
                p < 80 -> "70-80%"
                p < 90 -> "80-90%"
                else   -> "90-100%"
            }
            rangeCounts[key] = rangeCounts.getOrDefault(key, 0) + 1
        }

        return AnalyticsResponse(
            totalStudents = stats.totalStudents,
            totalSubjects = totalSubjects,
            averageAttendance = (stats.avgAttendance * 100.0).roundToLong() / 100.0,
            above70 = stats.above70,
            below60 = stats.below60,
            distribution = distribution,
            ranges = rangeCounts.map { (range, count) -> RangeItem(range, count) }
        )
    }
}
