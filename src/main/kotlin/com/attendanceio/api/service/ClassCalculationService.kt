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
    @Value("\${app.classes.start-date:}") private val startDateString: String
) {
    private val classStartDate: LocalDate? by lazy {
        if (startDateString.isBlank()) {
            null
        } else {
            try {
                LocalDate.parse(startDateString, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }

    /**
     * Calculate total expected classes for a subject based on student's timetable
     * @param timetableEntries List of timetable entries for the subject
     * @param endDate Date up to which to count classes (defaults to today)
     * @return Total number of expected classes
     */
    fun calculateTotalClasses(
        timetableEntries: List<DMStudentTimetable>,
        endDate: LocalDate = LocalDate.now()
    ): Int {
        if (classStartDate == null || timetableEntries.isEmpty()) {
            return 0
        }

        val start = classStartDate!!
        if (endDate.isBefore(start)) {
            return 0
        }

        // Get unique days of week for this subject
        val daysOfWeek = timetableEntries
            .mapNotNull { it.day?.name }
            .distinct()
            .mapNotNull { dayName ->
                when (dayName.uppercase()) {
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
            .toSet()

        if (daysOfWeek.isEmpty()) {
            return 0
        }

        // Count occurrences of each day of week between start and end date
        var totalClasses = 0
        var currentDate = start

        while (!currentDate.isAfter(endDate)) {
            if (currentDate.dayOfWeek in daysOfWeek) {
                totalClasses++
            }
            currentDate = currentDate.plusDays(1)
        }

        return totalClasses
    }
}

