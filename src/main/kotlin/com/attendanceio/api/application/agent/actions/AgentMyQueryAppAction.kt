package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.attendance.actions.GetMyAttendanceAppAction
import com.attendanceio.api.model.agent.AgentLectureOnDate
import com.attendanceio.api.model.agent.AgentMyAttendance
import com.attendanceio.api.model.agent.AgentMySubjectStats
import com.attendanceio.api.model.agent.AgentTimetable
import com.attendanceio.api.model.agent.AgentTimetableEntry
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * The calling student's own data. `GetMyAttendanceAppAction` is reused untouched — it is the
 * same calculation the Dashboard shows (timetable-derived totals, cancellations, classes needed,
 * bunkable classes) — and only enriched with subject codes/names the model can talk about.
 */
@Component
class AgentMyQueryAppAction(
    private val getMyAttendanceAppAction: GetMyAttendanceAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val catalog: AgentCatalogAppAction
) {
    fun myAttendance(studentId: Long, asOf: LocalDate?): AgentMyAttendance {
        val date = asOf ?: LocalDate.now()
        val response = getMyAttendanceAppAction.execute(studentId, date, "total")
        val subjectIds = (response.subjectStats.map { it.subjectId } + response.todayAttendance.map { it.subjectId })
            .mapNotNull { it.toLongOrNull() }.distinct()
        val subjects = subjectRepositoryAppAction.findAllById(subjectIds).associateBy { it.id }
        val stats = response.subjectStats.map { s ->
            val subject = subjects[s.subjectId.toLongOrNull()]
            AgentMySubjectStats(
                subjectId = s.subjectId.toLongOrNull() ?: 0,
                code = subject?.code ?: s.subjectId,
                name = subject?.name ?: "",
                present = s.present,
                absent = s.absent,
                total = s.total,
                percentage = s.percentage,
                classesNeeded = s.classesNeeded,
                bunkableClasses = s.bunkableClasses,
                totalUntilEndDate = s.totalUntilEndDate
            )
        }.sortedBy { it.code }
        val onDate = response.todayAttendance.map { t ->
            AgentLectureOnDate(
                subjectCode = subjects[t.subjectId.toLongOrNull()]?.code ?: t.subjectId,
                status = t.status.uppercase(),
                startTime = t.startTime,
                endTime = t.endTime,
                isExtraClass = t.isExtraClass
            )
        }
        return AgentMyAttendance(
            asOfDate = date.toString(),
            subjects = stats,
            lecturesOnDate = onDate,
            note = if (stats.isEmpty()) "No subjects with attendance yet for the current semester." else null
        )
    }

    fun myTimetable(studentId: Long): AgentTimetable {
        val semester = catalog.activeSemester()
        val semesterId = semester?.id ?: return AgentTimetable(catalog.label(semester), emptyList(), "No active semester.")
        val entries = studentTimetableRepositoryAppAction
            .findByStudentIdAndSemesterIdWithDetails(studentId, semesterId)
            .mapNotNull { e ->
                val subject = e.subject ?: return@mapNotNull null
                val start = e.customStartTime ?: e.slot?.startTime ?: return@mapNotNull null
                val end = e.customEndTime ?: e.slot?.endTime ?: return@mapNotNull null
                AgentTimetableEntry(
                    day = e.day?.name ?: "",
                    startTime = start.toString(),
                    endTime = end.toString(),
                    subjectCode = subject.code,
                    subjectName = subject.name,
                    location = subject.lecturePlace
                )
            }
            .sortedWith(compareBy({ dayOrder(it.day) }, { it.startTime }))
        return AgentTimetable(
            semesterLabel = catalog.label(semester),
            entries = entries,
            note = if (entries.isEmpty()) "No lecture timetable saved for this semester." else null
        )
    }

    private fun dayOrder(day: String): Int =
        listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY").indexOf(day.uppercase())
}
