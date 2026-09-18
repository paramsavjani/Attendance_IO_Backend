package com.attendanceio.api.model.agent

/*
 * What the tools hand back to the model. Deliberately compact: every field costs tokens on every
 * turn of the tool loop. Where an existing AppAction already returns a small DTO it is reused as
 * is; these types cover the shapes the model needs that nothing in the app returned before.
 */

/** Wraps any list a tool returns so the model always knows how much it did NOT see. */
data class AgentListResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val note: String? = null
)

data class AgentSubjectSummary(
    val subjectId: Long,
    val code: String,
    val name: String,
    val lecturePlace: String?,
    val semesterId: Long,
    val semesterLabel: String,
    /** Whether the calling student is enrolled in it (only set when the caller is a student). */
    val enrolledByMe: Boolean?
)

data class AgentSemesterSummary(
    val semesterId: Long,
    val year: Int,
    val type: String,
    val label: String,
    val isActive: Boolean
)

data class AgentMySubjectStats(
    val subjectId: Long,
    val code: String,
    val name: String,
    val present: Int,
    val absent: Int,
    val total: Int,
    val percentage: Double,
    /** Classes the student must attend in a row to reach their minimum criteria; 0 = already above. */
    val classesNeeded: Int,
    /** Classes the student can still miss and stay above the minimum criteria. */
    val bunkableClasses: Int,
    /** Scheduled classes until the semester's last class date. */
    val totalUntilEndDate: Int
)

data class AgentLectureOnDate(
    val subjectCode: String,
    val status: String,
    val startTime: String?,
    val endTime: String?,
    val isExtraClass: Boolean
)

data class AgentMyAttendance(
    val asOfDate: String,
    val subjects: List<AgentMySubjectStats>,
    /** Lectures recorded on `asOfDate` itself. */
    val lecturesOnDate: List<AgentLectureOnDate>,
    val note: String? = null
)

data class AgentAttendanceRecord(
    val date: String,
    val dayOfWeek: String,
    val status: String,
    val startTime: String?,
    val endTime: String?,
    val isExtraClass: Boolean
)

data class AgentSubjectRecords(
    val studentId: Long,
    val studentName: String,
    val rollNumber: String,
    val subjectId: Long,
    val subjectCode: String,
    val subjectName: String,
    val present: Int,
    val absent: Int,
    val cancelled: Int,
    /** Most recent first. */
    val records: List<AgentAttendanceRecord>,
    val totalRecords: Int,
    val lastAttendedOn: String?,
    val lastAbsentOn: String?,
    val note: String? = null
)

data class AgentStudentRank(
    val studentId: Long,
    val name: String,
    val rollNumber: String,
    val present: Int,
    val total: Int,
    val percentage: Double
)

/** One admission batch's slice of a subject (batch = first four digits of the roll number). */
data class AgentBatchAverage(
    val batch: String,
    val studentsEnrolled: Int,
    val studentsWithData: Int,
    val averagePercentage: Double?
)

data class AgentSubjectClassStats(
    val subjectId: Long,
    val subjectCode: String,
    val subjectName: String,
    val semesterLabel: String,
    /** APP = classes students marked in Attendance IO; OFFICIAL = the institute's published figures. */
    val basis: String,
    val studentsEnrolled: Int,
    val studentsWithData: Int,
    val averagePercentage: Double?,
    val above75Percent: Int,
    val below60Percent: Int,
    /** Per admission batch, best average first — a subject often mixes 2023/2024/2025 students. */
    val byBatch: List<AgentBatchAverage>,
    val top: List<AgentStudentRank>,
    val bottom: List<AgentStudentRank>,
    val note: String? = null
)

data class AgentGroupAverage(
    val groupDescription: String,
    val semesterLabel: String,
    val studentsInGroup: Int,
    val studentsWithData: Int,
    val averagePercentage: Double?,
    val medianPercentage: Double?,
    val above75Percent: Int,
    val between60And75Percent: Int,
    val below60Percent: Int,
    val note: String? = null
)

data class AgentTimetableEntry(
    val day: String,
    val startTime: String,
    val endTime: String,
    val subjectCode: String,
    val subjectName: String,
    val location: String?
)

data class AgentTimetable(
    val semesterLabel: String,
    val entries: List<AgentTimetableEntry>,
    val note: String? = null
)

data class AgentRangeCount(
    val range: String,
    val students: Int
)

data class AgentOverallAnalytics(
    val semesterLabel: String,
    val totalStudents: Int,
    val totalSubjects: Int,
    val averagePercentage: Double,
    val above70Percent: Int,
    val below60Percent: Int,
    val ranges: List<AgentRangeCount>,
    val note: String? = null
)

data class AgentSubjectAttendance(
    val subjectCode: String,
    val subjectName: String,
    val present: Int,
    val absent: Int,
    val total: Int,
    val percentage: Double?
)

data class AgentSemesterAttendance(
    val semesterId: Long,
    val label: String,
    /** True for the active semester — the one "this semester" refers to. */
    val isCurrent: Boolean,
    val subjects: List<AgentSubjectAttendance>
)

data class AgentStudentAttendance(
    val studentId: Long,
    val studentName: String,
    val rollNumber: String,
    /** APP = what the student marked; OFFICIAL = the institute's published figures. */
    val basis: String,
    val currentSemesterLabel: String?,
    /** Current semester first, then older ones. */
    val semesters: List<AgentSemesterAttendance>,
    val note: String? = null
)
