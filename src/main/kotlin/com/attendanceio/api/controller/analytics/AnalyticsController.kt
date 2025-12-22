package com.attendanceio.api.controller.analytics

import com.attendanceio.api.application.analytics.actions.CalculateAnalyticsAppAction
import com.attendanceio.api.model.analytics.AllSemestersResponse
import com.attendanceio.api.model.analytics.AnalyticsResponse
import com.attendanceio.api.model.analytics.SemesterAnalyticsResponse
import com.attendanceio.api.model.analytics.SemesterInfo
import com.attendanceio.api.model.analytics.SemesterWiseDataResponse
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController(
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val calculateAnalyticsAppAction: CalculateAnalyticsAppAction
) {
    @GetMapping("/semesters")
    fun getAllSemesters(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<List<SemesterInfo>> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        val semesters = semesterRepositoryAppAction.findAll()
            .sortedWith(compareByDescending<com.attendanceio.api.model.semester.DMSemester> { it.year }
                .thenByDescending { it.type })
            .map {
                SemesterInfo(
                    id = it.id ?: 0,
                    year = it.year,
                    type = it.type.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() },
                    label = "${it.year} ${it.type.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() }}"
                )
            }
        
        return ResponseEntity.ok(semesters)
    }
    
    @GetMapping
    fun getOverallAnalytics(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<AllSemestersResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        return try {
            // Get all semesters
            val semesters = semesterRepositoryAppAction.findAll()
                .sortedWith(compareByDescending<com.attendanceio.api.model.semester.DMSemester> { it.year }
                    .thenByDescending { it.type })
            
            val semesterInfos = semesters.map {
                SemesterInfo(
                    id = it.id ?: 0,
                    year = it.year,
                    type = it.type.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() },
                    label = "${it.year} ${it.type.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() }}"
                )
            }
            
            // Calculate overall analytics (all semesters aggregated)
            val overallAnalytics = calculateAnalyticsAppAction.calculateForSemester(null)
            
            // Calculate semester-wise data (limit to prevent timeout)
            // Only calculate for recent semesters (last 5) to improve performance
            val recentSemesters = semesters.take(5)
            val semesterWiseData = recentSemesters.map { semester ->
                try {
                    val analytics = calculateAnalyticsAppAction.calculateForSemester(semester)
                    val color = when {
                        analytics.averageAttendance >= 70 -> "hsl(var(--success))"
                        analytics.averageAttendance >= 60 -> "hsl(var(--warning))"
                        else -> "hsl(var(--destructive))"
                    }
                    
                    SemesterWiseDataResponse(
                        semester = "${semester.year} ${semester.type.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() }}",
                        percentage = analytics.averageAttendance,
                        students = analytics.totalStudents,
                        color = color
                    )
                } catch (e: Exception) {
                    // If calculation fails for a semester, skip it
                    SemesterWiseDataResponse(
                        semester = "${semester.year} ${semester.type.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() }}",
                        percentage = 0.0,
                        students = 0,
                        color = "hsl(var(--muted-foreground))"
                    )
                }
            }
            
            ResponseEntity.ok(
                AllSemestersResponse(
                    semesters = semesterInfos,
                    overall = overallAnalytics,
                    semesterWise = semesterWiseData
                )
            )
        } catch (e: Exception) {
            // Return error response if calculation times out or fails
            ResponseEntity.status(500).build()
        }
    }
    
    @GetMapping("/semester/{semesterId}")
    fun getSemesterAnalytics(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable semesterId: Long
    ): ResponseEntity<SemesterAnalyticsResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        return try {
            val semester = semesterRepositoryAppAction.findById(semesterId)
                ?: return ResponseEntity.status(404).build()
            
            val analytics = calculateAnalyticsAppAction.calculateForSemester(semester)
            
            val semesterInfo = SemesterInfo(
                id = semester.id ?: 0,
                year = semester.year,
                type = semester.type.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() },
                label = "${semester.year} ${semester.type.name.lowercase().replaceFirstChar { char -> char.uppercaseChar() }}"
            )
            
            ResponseEntity.ok(
                SemesterAnalyticsResponse(
                    semester = semesterInfo,
                    analytics = analytics
                )
            )
        } catch (e: Exception) {
            // Return error if calculation times out or fails
            ResponseEntity.status(500).build()
        }
    }
}

