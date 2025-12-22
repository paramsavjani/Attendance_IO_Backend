package com.attendanceio.api.application.analytics.actions

import com.attendanceio.api.model.analytics.AnalyticsResponse
import com.attendanceio.api.model.analytics.DistributionItem
import com.attendanceio.api.model.analytics.RangeItem
import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.repository.analytics.AnalyticsRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class CalculateAnalyticsAppAction(
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val analyticsRepositoryAppAction: AnalyticsRepositoryAppAction
) {
    
    /**
     * Calculate analytics using the pre-calculated database view
     * Results are cached for 5 minutes to improve performance
     */
    @Cacheable(value = ["analytics"], key = "#semester?.id ?: 'all'", unless = "#result.totalStudents == 0")
    fun calculateForSemester(semester: DMSemester?): AnalyticsResponse {
        val semesterId = semester?.id
        
        // Get statistics from the view (single fast query)
        val stats = analyticsRepositoryAppAction.getAnalyticsStats(semesterId)
        
        // Get total subjects
        val totalSubjects = analyticsRepositoryAppAction.getTotalSubjects(semesterId)
        
        // Get all attendance percentages for distribution and ranges
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
        
        // Calculate distribution (single pass)
        val above70Count = percentages.count { it >= 70 }
        val between60And70Count = percentages.count { it >= 60 && it < 70 }
        val below60Count = percentages.count { it < 60 }
        
        val totalWithData = percentages.size
        val distribution = listOf(
            DistributionItem(
                name = "Above 70%",
                value = if (totalWithData > 0) Math.round((above70Count.toDouble() / totalWithData) * 100).toInt() else 0,
                color = "hsl(var(--success))"
            ),
            DistributionItem(
                name = "60-75%",
                value = if (totalWithData > 0) Math.round((between60And70Count.toDouble() / totalWithData) * 100).toInt() else 0,
                color = "hsl(var(--warning))"
            ),
            DistributionItem(
                name = "Below 60%",
                value = if (totalWithData > 0) Math.round((below60Count.toDouble() / totalWithData) * 100).toInt() else 0,
                color = "hsl(var(--destructive))"
            )
        )
        
        // Calculate ranges (single pass)
        val rangeCounts = mutableMapOf<String, Int>().apply {
            put("0-20%", 0)
            put("20-40%", 0)
            put("40-60%", 0)
            put("60-70%", 0)
            put("70-80%", 0)
            put("80-90%", 0)
            put("90-100%", 0)
        }
        
        percentages.forEach { percentage ->
            when {
                percentage >= 0 && percentage < 20 -> rangeCounts["0-20%"] = rangeCounts["0-20%"]!! + 1
                percentage >= 20 && percentage < 40 -> rangeCounts["20-40%"] = rangeCounts["20-40%"]!! + 1
                percentage >= 40 && percentage < 60 -> rangeCounts["40-60%"] = rangeCounts["40-60%"]!! + 1
                percentage >= 60 && percentage < 70 -> rangeCounts["60-70%"] = rangeCounts["60-70%"]!! + 1
                percentage >= 70 && percentage < 80 -> rangeCounts["70-80%"] = rangeCounts["70-80%"]!! + 1
                percentage >= 80 && percentage < 90 -> rangeCounts["80-90%"] = rangeCounts["80-90%"]!! + 1
                percentage >= 90 && percentage <= 100 -> rangeCounts["90-100%"] = rangeCounts["90-100%"]!! + 1
            }
        }
        
        val ranges = rangeCounts.map { (range, count) -> RangeItem(range, count) }
        
        return AnalyticsResponse(
            totalStudents = stats.totalStudents,
            totalSubjects = totalSubjects,
            averageAttendance = Math.round(stats.avgAttendance * 100.0) / 100.0,
            above70 = stats.above70,
            below60 = stats.below60,
            distribution = distribution,
            ranges = ranges
        )
    }
}
