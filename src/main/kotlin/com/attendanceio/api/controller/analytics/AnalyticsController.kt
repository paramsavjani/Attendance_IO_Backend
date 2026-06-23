package com.attendanceio.api.controller.analytics

import com.attendanceio.api.application.analytics.actions.CalculateAnalyticsAppAction
import com.attendanceio.api.model.analytics.AllSemestersResponse
import com.attendanceio.api.model.analytics.AnalyticsResponse
import com.attendanceio.api.model.analytics.AppAnalyticsResponse
import com.attendanceio.api.model.analytics.SemesterAnalyticsResponse
import com.attendanceio.api.model.analytics.SemesterInfo
import com.attendanceio.api.model.analytics.SemesterWiseDataResponse
import com.attendanceio.api.model.semester.DMSemester
import com.attendanceio.api.repository.analytics.AnalyticsRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController(
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val calculateAnalyticsAppAction: CalculateAnalyticsAppAction,
    private val analyticsRepositoryAppAction: AnalyticsRepositoryAppAction
) {
    @GetMapping("/semesters")
    fun getAllSemesters(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<List<SemesterInfo>> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        return ResponseEntity.ok(sortedSemesters().map { it.toInfo() })
    }

    @GetMapping
    fun getOverallAnalytics(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<AllSemestersResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        return try {
            val semesters = sortedSemesters()
            val overallAnalytics = calculateAnalyticsAppAction.calculateForSemester(null)
            val semesterWise = semesters.take(5).map { semester ->
                try {
                    val analytics = calculateAnalyticsAppAction.calculateForSemester(semester)
                    SemesterWiseDataResponse(
                        semester = semester.label(),
                        percentage = analytics.averageAttendance,
                        students = analytics.totalStudents,
                        color = attendanceColor(analytics.averageAttendance)
                    )
                } catch (e: Exception) {
                    SemesterWiseDataResponse(
                        semester = semester.label(),
                        percentage = 0.0,
                        students = 0,
                        color = "hsl(var(--muted-foreground))"
                    )
                }
            }
            ResponseEntity.ok(AllSemestersResponse(semesters = semesters.map { it.toInfo() }, overall = overallAnalytics, semesterWise = semesterWise))
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }

    @GetMapping("/semester/{semesterId}")
    fun getSemesterAnalytics(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable semesterId: Long
    ): ResponseEntity<SemesterAnalyticsResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        return try {
            val semester = semesterRepositoryAppAction.findById(semesterId)
                ?: return ResponseEntity.status(404).build()
            val analytics = calculateAnalyticsAppAction.calculateForSemester(semester)
            ResponseEntity.ok(SemesterAnalyticsResponse(semester = semester.toInfo(), analytics = analytics))
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }

    @GetMapping("/app")
    fun getAppAnalytics(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestParam(required = false, defaultValue = "30") range: String?
    ): ResponseEntity<AppAnalyticsResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        return try {
            ResponseEntity.ok(analyticsRepositoryAppAction.getAppStats(range = range))
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }

    private fun sortedSemesters(): List<DMSemester> =
        semesterRepositoryAppAction.findAll()
            .sortedWith(compareByDescending<DMSemester> { it.year }.thenByDescending { it.type })

    private fun DMSemester.toInfo() = SemesterInfo(
        id = id ?: 0,
        year = year,
        type = typeLabel(),
        label = label()
    )

    private fun DMSemester.label() = "$year ${typeLabel()}"
    private fun DMSemester.typeLabel() = type.name.lowercase().replaceFirstChar { it.uppercaseChar() }

    private fun attendanceColor(percentage: Double) = when {
        percentage >= 70 -> "hsl(var(--success))"
        percentage >= 60 -> "hsl(var(--warning))"
        else -> "hsl(var(--destructive))"
    }
}
