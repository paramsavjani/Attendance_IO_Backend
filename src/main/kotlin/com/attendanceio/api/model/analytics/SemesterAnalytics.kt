package com.attendanceio.api.model.analytics

data class SemesterInfo(val id: Long, val year: Int, val type: String, val label: String)

data class SemesterAnalyticsResponse(val semester: SemesterInfo, val analytics: AnalyticsResponse)

data class SemesterWiseDataResponse(val semester: String, val percentage: Double, val students: Int, val color: String)

data class AllSemestersResponse(
    val semesters: List<SemesterInfo>,
    val overall: AnalyticsResponse,
    val semesterWise: List<SemesterWiseDataResponse>
)
