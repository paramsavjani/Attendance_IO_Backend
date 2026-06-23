package com.attendanceio.api.service

import com.attendanceio.api.model.timetable.DMStudentTimetable
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class ClassCalculationService(
    @Value("\${app.classes.start-date:}") private val startDateString: String,
    @Value("\${app.classes.end-date:}") private val endDateString: String
) {
    companion object {
        fun parseDayOfWeek(name: String?): DayOfWeek? = when (name?.uppercase()) {
            "MONDAY" -> DayOfWeek.MONDAY
            "TUESDAY" -> DayOfWeek.TUESDAY
            "WEDNESDAY" -> DayOfWeek.WEDNESDAY
            "THURSDAY" -> DayOfWeek.THURSDAY
            "FRIDAY" -> DayOfWeek.FRIDAY
            "SATURDAY" -> DayOfWeek.SATURDAY
            "SUNDAY" -> DayOfWeek.SUNDAY
            else -> null
        }
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
        excludedDates: Set<LocalDate> = emptySet()
    ): Int {
        val startDate = classStartDate ?: return 0
        if (timetableEntries.isEmpty()) return 0

        val effectiveEnd = if (classEndDate != null && endDate.isAfter(classEndDate)) classEndDate!! else endDate
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
        excludedDates: Set<LocalDate> = emptySet()
    ): Int {
        if (timetableEntries.isEmpty()) return 0
        val effectiveTo = if (classEndDate != null && toDate.isAfter(classEndDate)) classEndDate!! else toDate
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
        endDate: LocalDate = LocalDate.now()
    ): Int {
        val startDate = classStartDate ?: return 0
        if (daySlots.isEmpty()) return 0

        val effectiveEnd = if (classEndDate != null && endDate.isAfter(classEndDate)) classEndDate!! else endDate
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
