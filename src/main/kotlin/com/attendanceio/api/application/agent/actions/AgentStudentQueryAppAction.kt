package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.search.actions.GetStudentAttendanceAppAction
import com.attendanceio.api.application.search.actions.SearchStudentsAppAction
import com.attendanceio.api.model.agent.AgentAttendanceRecord
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentSubjectRecords
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.model.search.StudentSearchResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.format.TextStyle
import java.util.Locale

/**
 * Student lookups for the agent. Search and per-student attendance reuse the existing
 * AppActions as they are — they already hold the timetable/cancellation arithmetic and return
 * compact DTOs — so the chat answer and the Search page can never disagree.
 */
@Component
class AgentStudentQueryAppAction(
    private val searchStudentsAppAction: SearchStudentsAppAction,
    private val getStudentAttendanceAppAction: GetStudentAttendanceAppAction,
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
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

    /** `official` = the institute's published figures (available for past semesters); otherwise app-marked data. */
    fun studentAttendance(studentId: Long, official: Boolean): StudentAttendanceResponse? {
        val source = if (official) "INSTITUTE" else "STUDENT"
        return try {
            getStudentAttendanceAppAction.execute(studentId, source).copy(studentPictureUrl = null)
        } catch (e: IllegalArgumentException) {
            null
        }
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
                all.isEmpty() -> "No classes marked for this subject in the app."
                all.size > records.size -> "Showing the latest ${records.size} of ${all.size} records."
                else -> null
            }
        )
    }
}
