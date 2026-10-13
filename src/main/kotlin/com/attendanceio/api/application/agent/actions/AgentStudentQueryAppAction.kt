package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.search.actions.SearchStudentsAppAction
import com.attendanceio.api.model.agent.AgentAttendanceRecord
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentSemesterAttendance
import com.attendanceio.api.model.agent.AgentStudentAttendance
import com.attendanceio.api.model.agent.AgentSubjectAttendance
import com.attendanceio.api.model.agent.AgentSubjectRecords
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.search.StudentSearchResponse
import com.attendanceio.api.repository.agent.AgentAnalyticsRepositoryAppAction
import com.attendanceio.api.repository.agent.SubjectAttendanceStanding
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.format.TextStyle
import java.util.Locale

/**
 * Student lookups for the agent. Search reuses the existing AppAction as-is. Per-student
 * attendance goes through [AgentAnalyticsRepositoryAppAction.subjectStandings], which runs the
 * same arithmetic as the `student_attendance_analytics` view the Analytics page reads, so a chat
 * answer and the Analytics page can never disagree.
 */
@Component
class AgentStudentQueryAppAction(
    private val searchStudentsAppAction: SearchStudentsAppAction,
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val analytics: AgentAnalyticsRepositoryAppAction,
    private val catalog: AgentCatalogAppAction
) {
    fun searchStudents(query: String, limit: Int): AgentListResult<StudentSearchResponse> {
        val results = searchStudentsAppAction.execute(query).map { it.copy(pictureUrl = null) }
        return AgentListResult(
            items = results.take(limit),
            totalCount = results.size,
            note = if (results.isEmpty()) "No student matched; try a shorter part of the name or the roll number." else null
        )
    }

    /**
     * A student's standing per semester and subject, with both columns on every subject: the
     * institute's published figure up to its cutoff, and the total that counts today — those
     * official numbers plus the classes the student marked after that cutoff.
     *
     * [officialOnly] drops the app-marked part and reports the institute's figures alone, for when
     * someone explicitly asks what the institute published. It is *not* the default, because
     * "what is X's attendance" means the number that is true now, not the one frozen at the cutoff.
     *
     * The active semester is flagged and listed first so "this semester" can never be misread as
     * an older one.
     */
    fun studentAttendance(studentId: Long, officialOnly: Boolean): AgentStudentAttendance? {
        val student = studentRepositoryAppAction.findById(studentId) ?: return null
        val standings = analytics.subjectStandings(studentId).filter { it.hasAnything || it.hasOfficial }
        val active = catalog.activeSemester()

        val semesters = standings
            .groupBy { it.semesterId }
            .map { (semesterId, rows) ->
                AgentSemesterAttendance(
                    semesterId = semesterId,
                    label = "${rows.first().semesterYear} ${rows.first().semesterType}" +
                        if (semesterId == active?.id) " (current)" else "",
                    isCurrent = semesterId == active?.id,
                    subjects = rows
                        .mapNotNull { row -> subjectEntry(row, officialOnly) }
                        .sortedBy { it.subjectCode }
                )
            }
            .filter { it.subjects.isNotEmpty() }
            .sortedWith(compareByDescending<AgentSemesterAttendance> { it.isCurrent }.thenByDescending { it.semesterId })

        val cutoffs = standings.mapNotNull { it.cutoffDate }
        return AgentStudentAttendance(
            studentId = studentId,
            studentName = student.name ?: "",
            rollNumber = student.sid,
            basis = if (officialOnly) "OFFICIAL" else "OFFICIAL_PLUS_APP",
            currentSemesterLabel = catalog.label(active),
            semesters = semesters,
            note = when {
                semesters.isEmpty() && officialOnly -> "No official figures published for this student yet."
                semesters.isEmpty() -> "No attendance on record for this student."
                officialOnly -> "The institute's own figures only" +
                    (cutoffs.maxOrNull()?.let { ", as of $it" } ?: "") + "."
                cutoffs.isEmpty() -> "App-marked data only; the institute has published nothing for these subjects."
                else -> "Totals are the institute's figures up to ${cutoffs.max()} plus what the student marked after that."
            }
        )
    }

    /** One subject as the agent sees it: official column, combined column, or nothing to report. */
    private fun subjectEntry(row: SubjectAttendanceStanding, officialOnly: Boolean): AgentSubjectAttendance? {
        val officialPercentage =
            if (row.hasOfficial) Math.round(row.officialPresent * 1000.0 / row.officialTotal) / 10.0 else null

        if (officialOnly) {
            if (!row.hasOfficial) return null
            return AgentSubjectAttendance(
                subjectCode = row.subjectCode,
                subjectName = row.subjectName,
                present = row.officialPresent,
                absent = row.officialTotal - row.officialPresent,
                total = row.officialTotal,
                percentage = officialPercentage,
                officialPresent = row.officialPresent,
                officialTotal = row.officialTotal,
                officialPercentage = officialPercentage,
                officialAsOf = row.cutoffDate?.toString()
            )
        }

        if (!row.hasAnything) return null
        return AgentSubjectAttendance(
            subjectCode = row.subjectCode,
            subjectName = row.subjectName,
            present = row.combinedPresent,
            absent = row.combinedTotal - row.combinedPresent,
            total = row.combinedTotal,
            percentage = Math.round(row.combinedPresent * 1000.0 / row.combinedTotal) / 10.0,
            officialPresent = if (row.hasOfficial) row.officialPresent else null,
            officialTotal = if (row.hasOfficial) row.officialTotal else null,
            officialPercentage = officialPercentage,
            officialAsOf = row.cutoffDate?.toString(),
            markedSinceCutoff = row.presentAfter + row.absentAfter
        )
    }

    fun subjectRecords(studentId: Long, subjectQuery: String, semesterId: Long?, limit: Int): AgentSubjectRecords? {
        val student = studentRepositoryAppAction.findById(studentId) ?: return null
        val subject = catalog.resolveSubject(subjectQuery, semesterId) ?: return null
        val subjectId = subject.id ?: return null
        val all = attendanceRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            .filter { it.lectureDate != null }
            .sortedWith(compareByDescending<com.attendanceio.api.model.attendance.DMAttendance> { it.lectureDate }
                .thenByDescending { it.customStartTime ?: it.timeSlot?.startTime })
        val records = all.take(limit).map { a ->
            AgentAttendanceRecord(
                date = a.lectureDate.toString(),
                dayOfWeek = a.lectureDate!!.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                status = a.status.name,
                startTime = (a.customStartTime ?: a.timeSlot?.startTime)?.toString(),
                endTime = (a.customEndTime ?: a.timeSlot?.endTime)?.toString(),
                isExtraClass = a.isExtraClass
            )
        }
        return AgentSubjectRecords(
            studentId = studentId,
            studentName = student.name ?: "",
            rollNumber = student.sid,
            subjectId = subjectId,
            subjectCode = subject.code,
            subjectName = subject.name,
            present = all.count { it.status == AttendanceStatus.PRESENT },
            absent = all.count { it.status == AttendanceStatus.ABSENT },
            cancelled = all.count { it.status == AttendanceStatus.CANCELLED },
            records = records,
            totalRecords = all.size,
            lastAttendedOn = all.firstOrNull { it.status == AttendanceStatus.PRESENT }?.lectureDate?.toString(),
            lastAbsentOn = all.firstOrNull { it.status == AttendanceStatus.ABSENT }?.lectureDate?.toString(),
            note = when {
                all.isEmpty() -> "No classes marked for this subject in the app, so the dates of individual classes (including " +
                    "the last absence) are not known: the institute publishes totals only. Do not look for them day by day."
                all.size > records.size -> "Showing the latest ${records.size} of ${all.size} records."
                else -> null
            }
        )
    }
}
