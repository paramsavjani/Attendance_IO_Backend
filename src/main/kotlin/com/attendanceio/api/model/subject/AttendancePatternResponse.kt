package com.attendanceio.api.model.subject

data class AttendancePatternResponse(
    val academicYear: String,
    /** Every app user's marks this semester. */
    val overall: AttendancePatternSet,
    /** B.Tech students only, one entry per year of study (1st to 4th). */
    val years: List<YearAttendancePatterns>
)

data class YearAttendancePatterns(
    val batch: String,
    val yearOfStudy: Int,
    val patterns: AttendancePatternSet
)

data class AttendancePatternSet(
    val students: Int,
    val total: AttendancePatternBucket,
    val byWeekday: List<AttendancePatternBucket>,
    val byHour: List<AttendancePatternBucket>,
    val byKind: List<AttendancePatternBucket>
)

data class AttendancePatternBucket(
    val key: String,
    val label: String,
    val present: Int,
    val absent: Int,
    val percentage: Double
)
