package com.attendanceio.api.service

import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.repository.student.StudentRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class ClassCalculationService(
    @Value("\${app.classes.start-date:}") private val startDateString: String,
    @Value("\${app.classes.end-date:}") private val endDateString: String,
    /**
     * Students whose last class day differs from [endDateString], by sid prefix:
     * "202601|202603=2026-11-27;202631=2026-11-28" (prefixes split by |, groups by ;).
     */
    @Value("\${app.classes.batch-end-dates:}") private val batchEndDatesString: String,
    private val studentRepository: StudentRepository
) {
    companion object {
        fun parseDayOfWeek(name: String?): DayOfWeek? =
            name?.uppercase()?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
    }

    private val classStartDate: LocalDate? by lazy {
        if (startDateString.isBlank()) null
        else try { LocalDate.parse(startDateString, DateTimeFormatter.ISO_LOCAL_DATE) }
             catch (e: DateTimeParseException) { null }
    }

    private val classEndDate: LocalDate? by lazy {
        if (endDateString.isBlank()) null
        else try { LocalDate.parse(endDateString, DateTimeFormatter.ISO_LOCAL_DATE) }
             catch (e: DateTimeParseException) { null }
    }

    /** sid prefix -> that group's own last class day; longest prefix wins. */
    private val prefixEndDates: List<Pair<String, LocalDate>> by lazy {
        batchEndDatesString.split(';').flatMap { group ->
            val parts = group.split('=').map { it.trim() }
            val date = parts.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (parts.size != 2 || date == null) emptyList()
            else parts[0].split('|').map { it.trim() }.filter { it.isNotEmpty() }.map { it to date }
        }.sortedByDescending { it.first.length }
    }

    /** Last class day for a student: their group's own date, else the configured end date. */
    fun endDateFor(sid: String?): LocalDate? =
        sid?.let { s -> prefixEndDates.firstOrNull { s.startsWith(it.first) }?.second } ?: classEndDate

    /** [endDateFor] by student id (looks up the sid). */
    fun endDateForStudent(studentId: Long): LocalDate? =
        endDateFor(studentRepository.findById(studentId).orElse(null)?.sid)

    /** True once a student's semester has no more classes (after their last class day). */
    fun classesOverFor(sid: String?, date: LocalDate): Boolean =
        endDateFor(sid)?.let { date.isAfter(it) } ?: false

    fun calculateTotalClasses(
        timetableEntries: List<DMStudentTimetable>,
        endDate: LocalDate = LocalDate.now()
    ): Int = countScheduledClassesExcludingDates(timetableEntries, endDate)

    /**
     * Count scheduled timetable classes up to [endDate], skipping any dates in [excludedDates].
     * Respects the configured class start/end date window.
     */
    fun countScheduledClassesExcludingDates(
        timetableEntries: List<DMStudentTimetable>,
        endDate: LocalDate,
        excludedDates: Set<LocalDate> = emptySet(),
        classEnd: LocalDate? = classEndDate
    ): Int {
        val startDate = classStartDate ?: return 0
        if (timetableEntries.isEmpty()) return 0

        val effectiveEnd = if (classEnd != null && endDate.isAfter(classEnd)) classEnd else endDate
        if (effectiveEnd.isBefore(startDate)) return 0

        val timetableDays = timetableEntries.mapNotNull { parseDayOfWeek(it.day?.name) }
        if (timetableDays.isEmpty()) return 0

        var count = 0
        var date = startDate
        while (!date.isAfter(effectiveEnd)) {
            if (date !in excludedDates) {
                count += timetableDays.count { it == date.dayOfWeek }
            }
            date = date.plusDays(1)
        }
        return count
    }

    /**
     * Count timetable classes between [fromDate] and [toDate] inclusive,
     * skipping any dates in [excludedDates]. Useful for computing classes
     * after an official cutoff date. Respects the configured end-date ceiling.
     */
    fun countScheduledClassesBetween(
        timetableEntries: List<DMStudentTimetable>,
        fromDate: LocalDate,
        toDate: LocalDate,
        excludedDates: Set<LocalDate> = emptySet(),
        classEnd: LocalDate? = classEndDate
    ): Int {
        if (timetableEntries.isEmpty()) return 0
        val effectiveTo = if (classEnd != null && toDate.isAfter(classEnd)) classEnd else toDate
        if (effectiveTo.isBefore(fromDate)) return 0

        val timetableDays = timetableEntries.mapNotNull { parseDayOfWeek(it.day?.name) }
        if (timetableDays.isEmpty()) return 0

        var count = 0
        var date = fromDate
        while (!date.isAfter(effectiveTo)) {
            if (date !in excludedDates) count += timetableDays.count { it == date.dayOfWeek }
            date = date.plusDays(1)
        }
        return count
    }

    fun getConfiguredEndDate(): LocalDate? = classEndDate

    fun getConfiguredStartDate(): LocalDate? = classStartDate

    fun calculateTotalClassesFromDays(
        daySlots: List<DayOfWeek>,
        endDate: LocalDate = LocalDate.now(),
        classEnd: LocalDate? = classEndDate
    ): Int {
        val startDate = classStartDate ?: return 0
        if (daySlots.isEmpty()) return 0

        val effectiveEnd = if (classEnd != null && endDate.isAfter(classEnd)) classEnd else endDate
        if (effectiveEnd.isBefore(startDate)) return 0

        var totalClasses = 0
        var currentDate = startDate
        while (!currentDate.isAfter(effectiveEnd)) {
            totalClasses += daySlots.count { it == currentDate.dayOfWeek }
            currentDate = currentDate.plusDays(1)
        }
        return totalClasses
    }
}
