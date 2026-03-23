package com.attendanceio.api.application.subject.actions

import com.attendanceio.api.model.subject.SubjectAnalysisEntry
import com.attendanceio.api.model.subject.SubjectAnalysisResponse
import com.attendanceio.api.model.subject.SubjectStudentEntry
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetSubjectAnalysisAppAction(
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction
) {
    fun execute(subjectId: Long? = null): SubjectAnalysisResponse {
        val officialRecords = if (subjectId != null) {
            instituteAttendanceRepositoryAppAction.findBySubjectIdAndIsOfficial(subjectId, true)
        } else {
            instituteAttendanceRepositoryAppAction.findByIsOfficial(true)
        }

        val entries = buildEntries(officialRecords)
        return SubjectAnalysisResponse(subjects = entries)
    }

    fun executeByCode(subjectCode: String): SubjectAnalysisEntry? {
        val subject = subjectRepositoryAppAction.findByCode(subjectCode) ?: return null
        val subjectId = subject.id ?: return null
        val officialRecords = instituteAttendanceRepositoryAppAction.findBySubjectIdAndIsOfficial(subjectId, true)
        if (officialRecords.isEmpty()) return null
        return buildEntries(officialRecords).firstOrNull()
    }

    private fun buildEntries(officialRecords: List<com.attendanceio.api.model.attendance.DMInstituteAttendance>): List<SubjectAnalysisEntry> {
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

            val cutoff = records.maxByOrNull { it.cutoffDate ?: java.time.LocalDate.MIN }?.cutoffDate

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
