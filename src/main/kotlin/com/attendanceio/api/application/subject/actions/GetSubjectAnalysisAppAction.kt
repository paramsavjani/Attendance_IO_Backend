package com.attendanceio.api.application.subject.actions

import com.attendanceio.api.model.attendance.DMInstituteAttendance
import com.attendanceio.api.model.subject.SubjectAnalysisEntry
import com.attendanceio.api.model.subject.SubjectAnalysisResponse
import com.attendanceio.api.model.subject.SubjectStudentEntry
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

@Component
@Transactional(readOnly = true)
class GetSubjectAnalysisAppAction(
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction
) {
    @Cacheable(value = ["subjectAnalysis"], key = "'list'")
    fun execute(subjectId: Long? = null): SubjectAnalysisResponse {
        if (subjectId != null) {
            val records = instituteAttendanceRepositoryAppAction.findBySubjectIdAndIsOfficial(subjectId, true)
            return SubjectAnalysisResponse(subjects = buildEntries(records))
        }

        val summaryRows = instituteAttendanceRepositoryAppAction.getSubjectAnalysisSummary()
        val entries = summaryRows.map { row ->
            val cutoffDate = when (val v = row[7]) {
                is LocalDate -> v.toString()
                is Date -> v.toLocalDate().toString()
                else -> null
            }
            SubjectAnalysisEntry(
                subjectId = (row[0] as Number).toString(),
                subjectCode = row[1] as String,
                subjectName = row[2] as String,
                color = row[3] as? String ?: "#3B82F6",
                totalStudents = (row[5] as Number).toInt(),
                averageAttendancePercentage = Math.round((row[6] as Number).toDouble() * 100.0) / 100.0,
                cutoffDate = cutoffDate,
                students = emptyList()
            )
        }
        return SubjectAnalysisResponse(subjects = entries)
    }

    /**
     * If several `subjects` rows share the same code (e.g. different semesters), only the
     * most recently added row is used — highest numeric id first (see `OrderByIdDesc`).
     */
    @Cacheable(value = ["subjectAnalysis"], key = "#subjectCode.toLowerCase()")
    fun executeByCode(subjectCode: String): SubjectAnalysisEntry? {
        val code = subjectCode.trim()
        val candidates = subjectRepositoryAppAction.findAllByCodeIgnoreCaseOrderByIdDesc(code)
        if (candidates.isEmpty()) return null

        val subject = candidates.first()
        val subjectId = subject.id ?: return null
        val records = instituteAttendanceRepositoryAppAction.findBySubjectIdAndIsOfficial(subjectId, true)
        if (records.isEmpty()) return null
        return buildEntries(records).firstOrNull()
    }

    private fun buildEntries(officialRecords: List<DMInstituteAttendance>): List<SubjectAnalysisEntry> {
        val grouped = officialRecords.groupBy { it.subject?.id }

        return grouped.mapNotNull { (subjId, records) ->
            val firstRecord = records.firstOrNull() ?: return@mapNotNull null
            val subject = firstRecord.subject ?: return@mapNotNull null

            val students = records.mapNotNull { record ->
                val student = record.student ?: return@mapNotNull null
                val total = record.totalClasses
                val present = record.presentClasses
                val absent = total - present
                val percentage = if (total > 0) present * 100.0 / total else 0.0

                SubjectStudentEntry(
                    studentId = student.id.toString(),
                    rollNumber = student.sid,
                    studentName = student.name ?: "",
                    totalClasses = total,
                    presentClasses = present,
                    absentClasses = absent,
                    attendancePercentage = Math.round(percentage * 100.0) / 100.0
                )
            }.sortedBy { it.rollNumber }

            val avgPercentage = if (students.isNotEmpty()) {
                students.map { it.attendancePercentage }.average()
            } else 0.0

            val cutoff = records.maxByOrNull { it.cutoffDate ?: LocalDate.MIN }?.cutoffDate

            SubjectAnalysisEntry(
                subjectId = subjId.toString(),
                subjectCode = subject.code,
                subjectName = subject.name,
                color = subject.color,
                totalStudents = students.size,
                averageAttendancePercentage = Math.round(avgPercentage * 100.0) / 100.0,
                cutoffDate = cutoff?.toString(),
                students = students
            )
        }.sortedBy { it.subjectCode }
    }
}
