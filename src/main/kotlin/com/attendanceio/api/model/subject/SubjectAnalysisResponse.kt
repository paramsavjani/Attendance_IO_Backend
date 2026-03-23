package com.attendanceio.api.model.subject

data class SubjectAnalysisResponse(
    val subjects: List<SubjectAnalysisEntry>
)

data class SubjectAnalysisEntry(
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val color: String,
    val totalStudents: Int,
    val averageAttendancePercentage: Double,
    val cutoffDate: String?,
    val students: List<SubjectStudentEntry> = emptyList()
)

data class SubjectStudentEntry(
    val studentId: String,
    val rollNumber: String,
    val studentName: String,
    val totalClasses: Int,
    val presentClasses: Int,
    val absentClasses: Int,
    val attendancePercentage: Double
)
