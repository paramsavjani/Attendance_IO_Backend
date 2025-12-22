package com.attendanceio.api.model.analytics

data class AnalyticsResponse(
    val totalStudents: Int,
    val totalSubjects: Int,
    val averageAttendance: Double,
    val above70: Int,
    val below60: Int,
    val distribution: List<DistributionItem>,
    val ranges: List<RangeItem>
)

data class DistributionItem(
    val name: String,
    val value: Int,
    val color: String
)

data class RangeItem(
    val range: String,
    val count: Int
)

data class SemesterAnalyticsResponse(
    val semester: SemesterInfo,
    val analytics: AnalyticsResponse
)

data class SemesterInfo(
    val id: Long,
    val year: Int,
    val type: String,
    val label: String
)

data class SemesterWiseDataResponse(
    val semester: String,
    val percentage: Double,
    val students: Int,
    val color: String
)

data class AllSemestersResponse(
    val semesters: List<SemesterInfo>,
    val overall: AnalyticsResponse,
    val semesterWise: List<SemesterWiseDataResponse>
)

