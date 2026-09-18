package com.attendanceio.api.application.agent.actions

import com.attendanceio.api.application.attendance.actions.GetLabTutorialAttendanceAppAction
import com.attendanceio.api.application.attendance.actions.GetMyAttendanceAppAction
import com.attendanceio.api.application.search.actions.GetStudentAttendanceAppAction
import com.attendanceio.api.model.agent.AgentAcademicCalendar
import com.attendanceio.api.model.agent.AgentComparedCell
import com.attendanceio.api.model.agent.AgentComparedStudent
import com.attendanceio.api.model.agent.AgentComparedSubject
import com.attendanceio.api.model.agent.AgentComparison
import com.attendanceio.api.model.agent.AgentDayLecture
import com.attendanceio.api.model.agent.AgentDayReport
import com.attendanceio.api.model.agent.AgentMySubjectStats
import com.attendanceio.api.model.agent.AgentScheduleEntry
import com.attendanceio.api.model.agent.AgentSimulation
import com.attendanceio.api.model.agent.AgentSubjectSchedule
import com.attendanceio.api.model.agent.AgentTrend
import com.attendanceio.api.model.agent.AgentUnmarkedLectures
import com.attendanceio.api.model.agent.AgentWeekBucket
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.schedule.SubjectScheduleRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * "Plan and compare" questions: side-by-side comparisons, what-if simulations, what was missed,
 * weekly trends, schedules and the academic calendar. Built on the same actions the app's own
 * pages use, so numbers agree with what students already see.
 */
@Component
class AgentPlanningQueryAppAction(
    private val getStudentAttendanceAppAction: GetStudentAttendanceAppAction,
    private val getMyAttendanceAppAction: GetMyAttendanceAppAction,
    private val getLabTutorialAttendanceAppAction: GetLabTutorialAttendanceAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val subjectScheduleRepositoryAppAction: SubjectScheduleRepositoryAppAction,
    private val classCalculationService: ClassCalculationService,
    private val myQuery: AgentMyQueryAppAction,
    private val catalog: AgentCatalogAppAction
) {
    /** Current-semester attendance of several students side by side, over the subjects they share. */
    fun compare(studentIds: List<Long>, sharedOnly: Boolean): AgentComparison? {
        val active = catalog.activeSemester() ?: return null
        val ids = studentIds.distinct().take(6)
        if (ids.size < 2) return null

        data class Row(val id: Long, val name: String, val roll: String, val bySubject: Map<String, Triple<Int, Int, Int>>, val names: Map<String, String>)
        val rows = ids.mapNotNull { id ->
            val response = runCatching { getStudentAttendanceAppAction.execute(id, "STUDENT") }.getOrNull() ?: return@mapNotNull null
            val current = response.semesters.firstOrNull { it.semester.id.toLongOrNull() == active.id } ?: return@mapNotNull Row(id, response.studentName, response.rollNumber, emptyMap(), emptyMap())
            Row(
                id, response.studentName, response.rollNumber,
                current.subjects.associate { it.subjectCode to Triple(it.present, it.absent, it.total) },
                current.subjects.associate { it.subjectCode to it.subjectName }
            )
        }
        if (rows.size < 2) return null

        val codes = if (sharedOnly) {
            rows.map { it.bySubject.keys }.reduce { a, b -> a intersect b }
        } else {
            rows.flatMap { it.bySubject.keys }.toSet()
        }.sorted()
        val subjectNames = rows.flatMap { it.names.entries }.associate { it.key to it.value }

        val subjects = codes.map { code ->
            AgentComparedSubject(
                subjectCode = code,
                subjectName = subjectNames[code] ?: "",
                cells = rows.map { r ->
                    val t = r.bySubject[code]
                    AgentComparedCell(r.id, t?.first ?: 0, t?.second ?: 0, t?.third ?: 0, t?.let { pct(it.first, it.third) })
                }
            )
        }
        val students = rows.map { r ->
            val pcts = subjects.mapNotNull { s -> s.cells.first { it.studentId == r.id }.percentage }
            AgentComparedStudent(r.id, r.name, r.roll, pcts.average().takeIf { pcts.isNotEmpty() }?.round1())
        }
        return AgentComparison(
            semesterLabel = catalog.label(active),
            students = students,
            subjects = subjects,
            sharedOnly = sharedOnly,
            note = when {
                subjects.isEmpty() && sharedOnly -> "These students share no subject this semester; call again with sharedOnly=false to see each one's subjects."
                rows.size < ids.size -> "${ids.size - rows.size} of the requested ids were not found."
                else -> "App-marked data for the current semester; percentage = present / total scheduled so far."
            }
        )
    }

    /** What-if for the caller in one subject: skip/attend the next N classes and see the projected percentage. */
    fun simulate(studentId: Long, subjectQuery: String, skip: Int, attend: Int): AgentSimulation? {
        val subject = catalog.resolveSubject(subjectQuery, null) ?: return null
        val stat = myQuery.myAttendance(studentId, null).subjects.firstOrNull { it.subjectId == subject.id } ?: return null
        val minimum = subject.id?.let { studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, it)?.minimumCriteria } ?: 75
        val present = stat.present + attend
        val total = stat.total + skip + attend
        val projected = pct(present, total) ?: 0.0
        return AgentSimulation(
            subjectCode = subject.code,
            subjectName = subject.name,
            minimumCriteriaPercent = minimum,
            currentPresent = stat.present,
            currentTotal = stat.total,
            currentPercentage = stat.percentage,
            skip = skip,
            attend = attend,
            projectedPresent = present,
            projectedTotal = total,
            projectedPercentage = projected,
            staysAboveMinimum = projected >= minimum,
            classesNeededAfter = classesNeeded(present, total, minimum),
            bunkableAfter = bunkable(present, total, minimum),
            note = "Assumes no cancellations; the app's own dashboard counts scheduled classes the same way. Minimum criteria for this subject: $minimum%."
        )
    }

    /** Everything on one date for a student: marked lectures plus timetabled ones that were never marked. */
    fun dayReport(studentId: Long, date: LocalDate): AgentDayReport {
        val lectures = lecturesFor(studentId, date, date)
        return AgentDayReport(
            date = date.toString(),
            dayOfWeek = date.dayOfWeek.display(),
            lectures = lectures,
            note = if (lectures.isEmpty()) "No timetabled lectures and nothing marked on this date." else null
        )
    }

    /** Timetabled lectures in a date range with no attendance record at all (nothing marked, not even cancelled). */
    fun unmarked(studentId: Long, from: LocalDate, to: LocalDate): AgentUnmarkedLectures {
        val (start, end) = if (from.isAfter(to)) to to from else from to to
        val cappedEnd = if (ChronoUnit.DAYS.between(start, end) > 31) start.plusDays(31) else end
        val all = lecturesFor(studentId, start, cappedEnd).filter { it.status == "UNMARKED" }
        return AgentUnmarkedLectures(
            from = start.toString(),
            to = cappedEnd.toString(),
            lectures = all.take(40),
            totalUnmarked = all.size,
            note = buildString {
                append("Based on the student's lecture timetable; institute holidays are not known to the app, so a lecture on a holiday shows as unmarked until it is marked cancelled.")
                if (cappedEnd != end) append(" Range capped at 31 days.")
                if (all.size > 40) append(" Showing the first 40 of ${all.size}.")
            }
        )
    }

    /** Weekly present/absent/cancelled counts over the last N weeks, optionally for one subject. */
    fun trend(studentId: Long, subjectQuery: String?, weeks: Int): AgentTrend? {
        val subject = subjectQuery?.let { catalog.resolveSubject(it, null) }
        if (subjectQuery != null && subject == null) return null
        val today = LocalDate.now()
        val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
        val records = attendanceRepositoryAppAction.findByStudentIdAndLectureDateBetween(studentId, firstMonday, today)
            .filter { subject == null || it.subject?.id == subject.id }
        val buckets = (0 until weeks).map { i ->
            val ws = firstMonday.plusWeeks(i.toLong())
            val we = ws.plusDays(6)
            val inWeek = records.filter { it.lectureDate != null && !it.lectureDate!!.isBefore(ws) && !it.lectureDate!!.isAfter(we) }
            val p = inWeek.count { it.status == AttendanceStatus.PRESENT }
            val a = inWeek.count { it.status == AttendanceStatus.ABSENT }
            AgentWeekBucket(ws.toString(), we.toString(), p, a, inWeek.count { it.status == AttendanceStatus.CANCELLED }, pct(p, p + a))
        }
        return AgentTrend(
            studentId = studentId,
            subjectCode = subject?.code,
            weeks = buckets,
            note = "Counts what was marked in the app each week (Mon–Sun); percentage = present / (present + absent)."
        )
    }

    /** Lab + tutorial attendance of the caller (tracked separately from lectures in the app). */
    fun labTutorial(studentId: Long): List<AgentMySubjectStats> {
        val response = getLabTutorialAttendanceAppAction.execute(studentId)
        val subjects = subjectRepositoryAppAction.findAllById(response.subjectStats.mapNotNull { it.subjectId.toLongOrNull() }).associateBy { it.id }
        return response.subjectStats.map { s ->
            val subject = subjects[s.subjectId.toLongOrNull()]
            AgentMySubjectStats(
                subjectId = s.subjectId.toLongOrNull() ?: 0, code = subject?.code ?: s.subjectId, name = subject?.name ?: "",
                present = s.present, absent = s.absent, total = s.total, percentage = s.percentage,
                classesNeeded = s.classesNeeded, bunkableClasses = s.bunkableClasses, totalUntilEndDate = s.totalUntilEndDate
            )
        }.sortedBy { it.code }
    }

    /** When and where a subject's lectures happen, regardless of who is asking. */
    fun subjectSchedule(subjectQuery: String, semesterId: Long?): AgentSubjectSchedule? {
        val subject = catalog.resolveSubject(subjectQuery, semesterId) ?: return null
        val entries = subject.id?.let { subjectScheduleRepositoryAppAction.findBySubjectId(it) }.orEmpty()
            .mapNotNull { e ->
                val start = e.slot?.startTime ?: return@mapNotNull null
                val end = e.slot?.endTime ?: return@mapNotNull null
                AgentScheduleEntry(e.day?.name ?: "", start.toString(), end.toString(), subject.lecturePlace)
            }
            .sortedWith(compareBy({ dayOrder(it.day) }, { it.startTime }))
        return AgentSubjectSchedule(
            subjectCode = subject.code,
            subjectName = subject.name,
            semesterLabel = catalog.label(subject.semester),
            lecturePlace = subject.lecturePlace,
            lectures = entries,
            note = if (entries.isEmpty()) "No lecture schedule recorded for this subject." else null
        )
    }

    fun calendar(): AgentAcademicCalendar {
        val active = catalog.activeSemester()
        val today = LocalDate.now()
        val end = classCalculationService.getConfiguredEndDate()
        val start = classCalculationService.getConfiguredStartDate()
        val teachingDays = end?.takeIf { !it.isBefore(today) }?.let { e ->
            generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(e) }.count { it.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        }
        return AgentAcademicCalendar(
            activeSemester = active?.let { catalog.listSemesters().firstOrNull { s -> s.semesterId == it.id } },
            classesStart = start?.toString(),
            classesEnd = end?.toString(),
            today = today.toString(),
            weeksRemaining = end?.let { ceil(ChronoUnit.DAYS.between(today, it).coerceAtLeast(0) / 7.0).toInt() },
            teachingDaysRemaining = teachingDays
        )
    }

    // ---- helpers ----

    private fun lecturesFor(studentId: Long, from: LocalDate, to: LocalDate): List<AgentDayLecture> {
        val active = catalog.activeSemester() ?: return emptyList()
        val timetable = studentTimetableRepositoryAppAction.findByStudentIdAndSemesterIdWithDetails(studentId, active.id!!)
        val records = attendanceRepositoryAppAction.findByStudentIdAndLectureDateBetween(studentId, from, to)
        val subjects = subjectRepositoryAppAction.findAllById(records.mapNotNull { it.subject?.id }.distinct()).associateBy { it.id }
        val out = mutableListOf<AgentDayLecture>()
        var d = from
        while (!d.isAfter(to)) {
            val dayName = d.dayOfWeek.name
            val marked = records.filter { it.lectureDate == d }
            val markedSubjectIds = marked.mapNotNull { it.subject?.id }.toSet()
            marked.forEach { r ->
                val subject = r.subject?.id?.let { subjects[it] }
                out += AgentDayLecture(d.toString(), d.dayOfWeek.display(), subject?.code ?: "", subject?.name ?: "",
                    (r.customStartTime ?: r.timeSlot?.startTime)?.toString(), (r.customEndTime ?: r.timeSlot?.endTime)?.toString(), r.status.name)
            }
            timetable.filter { it.day?.name.equals(dayName, ignoreCase = true) && it.subject?.id !in markedSubjectIds }
                .forEach { e ->
                    val s = e.subject ?: return@forEach
                    out += AgentDayLecture(d.toString(), d.dayOfWeek.display(), s.code, s.name,
                        (e.customStartTime ?: e.slot?.startTime)?.toString(), (e.customEndTime ?: e.slot?.endTime)?.toString(),
                        if (d.isAfter(LocalDate.now())) "UPCOMING" else "UNMARKED")
                }
            d = d.plusDays(1)
        }
        return out.sortedWith(compareBy({ it.date }, { it.startTime ?: "" }))
    }

    private fun DayOfWeek.display() = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    private fun dayOrder(day: String) = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY").indexOf(day.uppercase())
    private fun pct(present: Int, total: Int): Double? = if (total <= 0) null else (present * 100.0 / total).round1()
    private fun Double.round1(): Double = (this * 10).roundToInt() / 10.0

    /** Consecutive classes to attend so that present/total reaches [minimum]%. */
    private fun classesNeeded(present: Int, total: Int, minimum: Int): Int {
        if (total > 0 && present * 100.0 / total >= minimum) return 0
        // (present + n) / (total + n) >= m/100  →  n >= (m*total - 100*present) / (100 - m)
        return ceil((minimum * total - 100.0 * present) / (100 - minimum)).toInt().coerceAtLeast(0)
    }

    /** Classes that can still be skipped while staying at/above [minimum]%. */
    private fun bunkable(present: Int, total: Int, minimum: Int): Int {
        if (total <= 0 || present * 100.0 / total < minimum) return 0
        // present / (total + n) >= m/100  →  n <= 100*present/m - total
        return (100.0 * present / minimum - total).toInt().coerceAtLeast(0)
    }
}
