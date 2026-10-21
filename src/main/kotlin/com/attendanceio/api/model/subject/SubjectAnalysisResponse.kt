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
    val students: List<SubjectStudentEntry> = emptyList(),
    val stats: SubjectAttendanceStats? = null
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

data class SubjectAttendanceStats(
    val mean: Double,
    val median: Double,
    val standardDeviation: Double,
    val min: Double,
    val max: Double,
    val p10: Double,
    val q1: Double,
    val q3: Double,
    val p90: Double,
    val iqr: Double,
    /** Mean after dropping values outside [q1 - 1.5·IQR, q3 + 1.5·IQR]. */
    val trimmedMean: Double,
    val outlierLowerBound: Double,
    val outlierUpperBound: Double,
    val lowOutlierCount: Int,
    val highOutlierCount: Int,
    /** Most populated 10-point histogram bucket, e.g. "80-90". */
    val modeRange: String,
    val modeRangeCount: Int,
    val below70Count: Int,
    /** Students in [70, 75) — passing, but one or two absences away from the cutoff. */
    val borderlineCount: Int,
    val fullAttendanceCount: Int,
    val maxClassesConducted: Int,
    val averageAbsences: Double,
    val medianAbsences: Double,
    val histogram: List<AttendanceBucket>,
    val bands: List<AttendanceBucket>,
    val absenceDistribution: List<AbsenceCount>
)

data class AttendanceBucket(
    val label: String,
    val from: Double,
    val to: Double,
    val count: Int,
    val percentage: Double
)

data class AbsenceCount(
    val absences: Int,
    val count: Int
)
