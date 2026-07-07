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

data class DistributionItem(val name: String, val value: Int, val color: String)

data class RangeItem(val range: String, val count: Int)
