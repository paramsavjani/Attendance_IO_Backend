package com.attendanceio.api.model.attendance

data class AttendanceCalculationResult(
    val subjectId: Long,
    val subjectCode: String,
    val subjectName: String,
    val semesterId: Long,
    val semesterYear: Int,
    val semesterType: String,
    val basePresent: Int,
    val baseAbsent: Int,
    val baseTotal: Int,
    val presentAfterCutoff: Int,
    val absentAfterCutoff: Int,
    val leaveAfterCutoff: Int,
    val totalAfterCutoff: Int
)

