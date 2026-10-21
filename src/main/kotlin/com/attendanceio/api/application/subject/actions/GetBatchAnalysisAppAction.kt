package com.attendanceio.api.application.subject.actions

import com.attendanceio.api.model.subject.BatchAnalysisEntry
import com.attendanceio.api.model.subject.BatchAnalysisResponse
import com.attendanceio.api.model.subject.BatchGroupStats
import com.attendanceio.api.model.subject.BranchAnalysisEntry
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Official attendance of B.Tech students grouped by admission batch (sid digits 1-4)
 * and branch (sid digits 5-6). A student's attendance is their overall official
 * percentage across all their subjects; group averages are averages over students.
 */
@Component
@Transactional(readOnly = true)
class GetBatchAnalysisAppAction(
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction
) {
    @Cacheable(value = ["subjectAnalysis"], key = "'batches'")
    fun execute(): BatchAnalysisResponse {
        val rows = instituteAttendanceRepositoryAppAction.getBtechStudentOverallAttendance()
            .map { row -> (row[0] as String) to (row[1] as Number).toDouble() }

        val today = LocalDate.now()
        // The academic year starts in July: Oct 2026 is AY 2026-27, so the 2026 batch is in 1st year.
        val academicStartYear = if (today.monthValue >= 7) today.year else today.year - 1

        val batches = rows.groupBy { (prefix, _) -> prefix.take(4) }
            .mapNotNull { (batch, students) ->
                val batchYear = batch.toIntOrNull() ?: return@mapNotNull null
                val branches = students.groupBy { (prefix, _) -> prefix }
                    .map { (prefix, branchStudents) ->
                        val code = prefix.drop(4)
                        BranchAnalysisEntry(
                            code = code,
                            sidPrefix = prefix,
                            label = BRANCH_LABELS[code],
                            stats = statsOf(branchStudents.map { it.second })
                        )
                    }
                    .sortedBy { it.code }
                BatchAnalysisEntry(
                    batch = batch,
                    yearOfStudy = academicStartYear - batchYear + 1,
                    stats = statsOf(students.map { it.second }),
                    branches = branches
                )
            }
            // B.Tech is four years; older batches are a handful of extended students.
            .filter { it.yearOfStudy in 1..4 }
            .sortedBy { it.yearOfStudy }

        return BatchAnalysisResponse(
            academicYear = "$academicStartYear-${(academicStartYear + 1) % 100}",
            batches = batches
        )
    }

    private fun statsOf(percentages: List<Double>): BatchGroupStats {
        val sorted = percentages.sorted()
        val n = sorted.size
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        return BatchGroupStats(
            students = n,
            averageAttendance = round2(sorted.average()),
            medianAttendance = round2(median),
            below70Count = sorted.count { it < 70.0 }
        )
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    companion object {
        /**
         * B.Tech branch codes (sid digits 5-6); unknown codes are shown by number.
         * Matched against first-semester PC1-<programme> enrolments of the 2026 batch.
         */
        val BRANCH_LABELS = mapOf(
            "01" to "ICT / ICT-CS",
            "03" to "MnC",
            "04" to "EVD",
            "05" to "CS-AI",
            "06" to "ECE-AI"
        )
    }
}
