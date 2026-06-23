package com.attendanceio.api.model.attendance

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstituteAttendanceJson(
    val cutoffDate: String?,
    val scrapedAt: String? = null,
    val totalSubjects: Int? = null,
    val totalRecords: Int? = null,
    val records: List<InstituteAttendanceRecordJson> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstituteAttendanceRecordJson(
    val subjectCode: String,
    val rollNumber: String,
    val studentName: String,
    val totalClasses: Int,
    val presentClasses: Int,
    val absentClasses: Int
)
