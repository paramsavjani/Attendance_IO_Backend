package com.attendanceio.api.model.subject

data class BatchAnalysisResponse(
    val academicYear: String,
    val batches: List<BatchAnalysisEntry>
)

/** Students admitted in one year (first four sid digits), with a split per branch. */
data class BatchAnalysisEntry(
    val batch: String,
    val yearOfStudy: Int,
    val stats: BatchGroupStats,
    val branches: List<BranchAnalysisEntry>
)

/** One branch of a batch — the 6-digit sid prefix, e.g. 202301. */
data class BranchAnalysisEntry(
    val code: String,
    val sidPrefix: String,
    val label: String?,
    val stats: BatchGroupStats
)

data class BatchGroupStats(
    val students: Int,
    val averageAttendance: Double,
    val medianAttendance: Double,
    val below70Count: Int
)
