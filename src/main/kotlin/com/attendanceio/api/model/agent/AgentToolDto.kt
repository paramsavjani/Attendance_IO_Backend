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
    /** Everyone under `belowPercent` when that filter was given (capped at 30), lowest first. */
    val below: List<AgentStudentRank>? = null,
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

/* ---- added in the second pass: comparisons, simulations, calendar-style questions ---- */

data class AgentComparedCell(
    val studentId: Long,
    val present: Int,
    val absent: Int,
    val total: Int,
    val percentage: Double?
)

data class AgentComparedSubject(
    val subjectCode: String,
    val subjectName: String,
    /** One entry per compared student, in the same order as [AgentComparison.students]. */
    val cells: List<AgentComparedCell>
)

data class AgentComparedStudent(
    val studentId: Long,
    val name: String,
    val rollNumber: String,
    /** Average over the subjects in the comparison. */
    val averagePercentage: Double?
)

data class AgentComparison(
    val semesterLabel: String,
    val students: List<AgentComparedStudent>,
    /** Only subjects all compared students share, unless [sharedOnly] is false. */
    val subjects: List<AgentComparedSubject>,
    val sharedOnly: Boolean,
    val note: String? = null
)

data class AgentSimulation(
    val subjectCode: String,
    val subjectName: String,
    val minimumCriteriaPercent: Int,
    val currentPresent: Int,
    val currentTotal: Int,
    val currentPercentage: Double,
    val skip: Int,
    val attend: Int,
    val projectedPresent: Int,
    val projectedTotal: Int,
    val projectedPercentage: Double,
    val staysAboveMinimum: Boolean,
    /** From the projected state: how many more consecutive classes to attend to reach the minimum (0 if already above). */
    val classesNeededAfter: Int,
    /** From the projected state: how many more classes could be skipped and still stay at/above the minimum. */
    val bunkableAfter: Int,
    val note: String? = null
)

data class AgentDayLecture(
    val date: String,
    val dayOfWeek: String,
    val subjectCode: String,
    val subjectName: String,
    val startTime: String?,
    val endTime: String?,
    /** PRESENT / ABSENT / CANCELLED, or UNMARKED when the timetable had a class but nothing was recorded. */
    val status: String
)

data class AgentDayReport(
    val date: String,
    val dayOfWeek: String,
    val lectures: List<AgentDayLecture>,
    val note: String? = null
)

data class AgentUnmarkedLectures(
    val from: String,
    val to: String,
    val lectures: List<AgentDayLecture>,
    val totalUnmarked: Int,
    val note: String? = null
)

data class AgentWeekBucket(
    val weekStart: String,
    val weekEnd: String,
    val present: Int,
    val absent: Int,
    val cancelled: Int,
    val percentage: Double?
)

data class AgentTrend(
    val studentId: Long,
    val subjectCode: String?,
    val weeks: List<AgentWeekBucket>,
    val note: String? = null
)

data class AgentScheduleEntry(
    val day: String,
    val startTime: String,
    val endTime: String,
    val room: String?
)

data class AgentSubjectSchedule(
    val subjectCode: String,
    val subjectName: String,
    val semesterLabel: String,
    val lecturePlace: String?,
    val lectures: List<AgentScheduleEntry>,
    val note: String? = null
)

data class AgentAcademicCalendar(
    val activeSemester: AgentSemesterSummary?,
    val classesStart: String?,
    val classesEnd: String?,
    val today: String,
    val weeksRemaining: Int?,
    val teachingDaysRemaining: Int?
)

// ---- alumni directory ----

/** One alumnus as the model sees them: who, where, how to reach them. LinkedIn only — no other links or source details. */
data class AgentAlumnus(
    val name: String,
    val title: String?,
    val company: String,
    /** Company's average package in LPA; null when not known. */
    val companyAvgLpa: java.math.BigDecimal?,
    val city: String?,
    val batch: Int?,
    val course: String?,
    val linkedinUrl: String?
)

data class AgentAlumniCompany(
    val name: String,
    val alumniCount: Int,
    val avgLpa: java.math.BigDecimal?
)
