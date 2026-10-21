package com.attendanceio.api.application.subject.actions

import com.attendanceio.api.model.subject.AttendancePatternBucket
import com.attendanceio.api.model.subject.AttendancePatternResponse
import com.attendanceio.api.model.subject.AttendancePatternSet
import com.attendanceio.api.model.subject.YearAttendancePatterns
import com.attendanceio.api.repository.analytics.AttendancePatternRepository
import com.attendanceio.api.repository.analytics.AttendancePatternRow
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * When do students attend? Attendance % of app-marked classes this semester by weekday,
 * start hour and class kind (lecture / lab / extra), overall and per B.Tech year of study.
 * A percentage is present / (present + absent) over marks, so every class counts once.
 */
@Component
@Transactional(readOnly = true)
class GetAttendancePatternsAppAction(
    private val attendancePatternRepository: AttendancePatternRepository
) {
    // The "analytics" cache is cleared whenever someone marks attendance.
    @Cacheable(value = ["analytics"], key = "'attendancePatterns'")
    fun execute(): AttendancePatternResponse {
        val rows = attendancePatternRepository.patternRows()
        val students = attendancePatternRepository.studentCountsByBatch()
        val startYear = AcademicCalendar.startYear()

        val years = rows.filter { it.batch != null }
            .groupBy { it.batch!! }
            .mapNotNull { (batch, batchRows) ->
                val batchYear = batch.toIntOrNull() ?: return@mapNotNull null
                val year = AcademicCalendar.yearOfStudy(batchYear, startYear)
                if (year !in 1..4) return@mapNotNull null
                YearAttendancePatterns(batch, year, patternSet(batchRows, students[batch] ?: 0))
            }
            .sortedBy { it.yearOfStudy }

        return AttendancePatternResponse(
            academicYear = AcademicCalendar.label(startYear),
            overall = patternSet(rows, students.values.sum()),
            years = years
        )
    }

    private fun patternSet(rows: List<AttendancePatternRow>, students: Int): AttendancePatternSet {
        val byWeekday = rows.groupBy { it.weekday }.toSortedMap().map { (day, group) ->
            val name = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            bucket(name.uppercase(), name, group)
        }
        val byHour = rows.filter { it.hour != null && it.hour in CLASS_HOURS }.groupBy { it.hour!! }.toSortedMap().map { (hour, group) ->
            bucket(hour.toString(), hourLabel(hour), group)
        }
        val byKind = KINDS.mapNotNull { (kind, label) ->
            rows.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { bucket(kind, label, it) }
        }
        return AttendancePatternSet(
            students = students,
            total = bucket("ALL", "All classes", rows),
            byWeekday = byWeekday.filter { it.present + it.absent >= MIN_BUCKET_MARKS },
            byHour = byHour.filter { it.present + it.absent >= MIN_BUCKET_MARKS },
            byKind = byKind.filter { it.present + it.absent >= MIN_BUCKET_MARKS }
        )
    }

    private fun bucket(key: String, label: String, rows: List<AttendancePatternRow>): AttendancePatternBucket {
        val present = rows.sumOf { it.present }
        val absent = rows.sumOf { it.absent }
        val total = present + absent
        val pct = if (total > 0) present * 100.0 / total else 0.0
        return AttendancePatternBucket(key, label, present, absent, Math.round(pct * 100.0) / 100.0)
    }

    private fun hourLabel(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }

    companion object {
        /** Buckets with fewer marks than this are too noisy to show. */
        const val MIN_BUCKET_MARKS = 50

        /** Classes start between 7 AM and 7 PM; other hours are mistyped custom times. */
        val CLASS_HOURS = 7..19

        val KINDS = listOf("LECTURE" to "Lecture", "LAB" to "Lab", "EXTRA" to "Extra class")
    }
}
