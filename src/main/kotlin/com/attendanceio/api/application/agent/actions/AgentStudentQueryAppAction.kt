package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.search.actions.GetStudentAttendanceAppAction
import com.attendanceio.api.application.search.actions.SearchStudentsAppAction
import com.attendanceio.api.model.agent.AgentAttendanceRecord
import com.attendanceio.api.model.agent.AgentListResult
import com.attendanceio.api.model.agent.AgentSemesterAttendance
import com.attendanceio.api.model.agent.AgentStudentAttendance
import com.attendanceio.api.model.agent.AgentSubjectAttendance
import com.attendanceio.api.model.agent.AgentSubjectRecords
import com.attendanceio.api.model.attendance.AttendanceStatus
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

    /**
     * `official` = the institute's published figures (available for past semesters); otherwise
     * app-marked data. The existing action returns every semester; here the active one is flagged
     * and listed first so "this semester" can never be misread as an older one.
     */
    fun studentAttendance(studentId: Long, official: Boolean): AgentStudentAttendance? {
        val source = if (official) "INSTITUTE" else "STUDENT"
        val response = try {
            getStudentAttendanceAppAction.execute(studentId, source)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val active = catalog.activeSemester()
        val semesters = response.semesters
            .map { sem ->
                val id = sem.semester.id.toLongOrNull() ?: 0
                AgentSemesterAttendance(
                    semesterId = id,
                    label = "${sem.semester.year} ${sem.semester.type}" + if (id == active?.id) " (current)" else "",
                    isCurrent = id == active?.id,
                    subjects = sem.subjects.map { sub ->
                        AgentSubjectAttendance(
                            subjectCode = sub.subjectCode,
                            subjectName = sub.subjectName,
                            present = sub.present,
                            absent = sub.absent,
                            total = sub.total,
                            percentage = if (sub.total > 0) Math.round(sub.present * 1000.0 / sub.total) / 10.0 else null
                        )
                    }.sortedBy { it.subjectCode }
                )
            }
            .sortedWith(compareByDescending<AgentSemesterAttendance> { it.isCurrent }.thenByDescending { it.semesterId })
        return AgentStudentAttendance(
            studentId = studentId,
            studentName = response.studentName,
            rollNumber = response.rollNumber,
            basis = if (official) "OFFICIAL" else "APP",
            currentSemesterLabel = catalog.label(active),
            semesters = semesters,
            note = when {
                semesters.isEmpty() && official -> "No official figures published for this student yet."
                semesters.isEmpty() -> "This student has not marked any attendance in the app."
                semesters.none { it.isCurrent } -> "No data for the current semester; only older semesters are available."
                else -> null
            }
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
                all.isEmpty() -> "No classes marked for this subject in the app."
                all.size > records.size -> "Showing the latest ${records.size} of ${all.size} records."
                else -> null
            }
        )
    }
}
