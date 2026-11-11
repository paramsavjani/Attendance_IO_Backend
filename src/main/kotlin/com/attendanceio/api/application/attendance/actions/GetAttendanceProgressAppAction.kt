package com.attendanceio.api.application.attendance.actions

import com.attendanceio.api.model.attendance.AttendanceProgressResponse
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.StreakInfo
import com.attendanceio.api.model.attendance.TrendPoint
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Feeds the weekly trend and streak home-screen widgets. */
@Component
class GetAttendanceProgressAppAction(
    private val getMyAttendanceAppAction: GetMyAttendanceAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val classCalculationService: ClassCalculationService
) {
    fun execute(studentId: Long, today: LocalDate, weeks: Int): AttendanceProgressResponse {
        val start = classCalculationService.getConfiguredStartDate()
        return AttendanceProgressResponse(
            trend = trend(studentId, today, weeks.coerceIn(2, 12), start),
            streak = streak(studentId, today, start)
        )
    }

    /**
     * Each point runs the same calculation the app shows, as of that date, so the newest point is
     * exactly the overall % on the home screen.
     */
    private fun trend(studentId: Long, today: LocalDate, weeks: Int, start: LocalDate?): List<TrendPoint> {
        val lastSunday = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val dates = (weeks - 2 downTo 0).map { lastSunday.minusWeeks(it.toLong()) } + today
        return dates
            .filter { start == null || !it.isBefore(start) }
            .map { date ->
                val stats = getMyAttendanceAppAction.execute(studentId, date, "total").subjectStats
                TrendPoint(date.toString(), stats.sumOf { it.present }, stats.sumOf { it.total })
            }
            .filter { it.total > 0 }
    }

    private fun streak(studentId: Long, today: LocalDate, start: LocalDate?): StreakInfo {
        val byDay = attendanceRepositoryAppAction.findByStudentId(studentId)
            .filter { a ->
                val d = a.lectureDate ?: return@filter false
                a.excludeFromAnalytics != true && !d.isAfter(today) && (start == null || !d.isBefore(start))
            }
            .groupBy { it.lectureDate!! }
            .toSortedMap()

        var run = 0
        var best = 0
        for ((_, marks) in byDay) {
            val present = marks.count { it.status == AttendanceStatus.PRESENT }
            val absent = marks.count { it.status == AttendanceStatus.ABSENT }
            if (present == 0 && absent == 0) continue // only cancelled classes that day
            run = if (absent == 0) run + 1 else 0
            best = maxOf(best, run)
        }
        return StreakInfo(current = run, best = best)
    }
}
