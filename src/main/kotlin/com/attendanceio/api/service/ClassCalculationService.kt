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
    
    private val classEndDate: LocalDate? by lazy {
        if (endDateString.isBlank()) {
            null
        } else {
            try {
                LocalDate.parse(endDateString, DateTimeFormatter.ISO_LOCAL_DATE)
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
        
        // Use the configured end date as the maximum limit, or the provided endDate, whichever is earlier
        val effectiveEndDate = if (classEndDate != null && endDate.isAfter(classEndDate)) {
            classEndDate!!
        } else {
            endDate
        }
        
        if (effectiveEndDate.isBefore(start)) {
            return 0
        }

        // Get timetable entries with their day of week
        // Each entry represents a unique day+slot combination (multiple lectures per day are possible)
        val timetableDaySlots = timetableEntries.mapNotNull { entry ->
            val dayName = entry.day?.name?.uppercase()
            val dayOfWeek = when (dayName) {
                "MONDAY" -> DayOfWeek.MONDAY
                "TUESDAY" -> DayOfWeek.TUESDAY
                "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                "THURSDAY" -> DayOfWeek.THURSDAY
                "FRIDAY" -> DayOfWeek.FRIDAY
                "SATURDAY" -> DayOfWeek.SATURDAY
                "SUNDAY" -> DayOfWeek.SUNDAY
                else -> null
            }
            dayOfWeek
        }

        if (timetableDaySlots.isEmpty()) {
            return 0
        }

        // Count occurrences: For each timetable entry (day+slot), count how many times
        // that day of week occurs between start and effective end date (inclusive)
        // This ensures multiple lectures on the same day are all counted
        var totalClasses = 0
        var currentDate = start

        while (!currentDate.isAfter(effectiveEndDate)) {
            // Count how many timetable entries match this day of week
            val dayOfWeek = currentDate.dayOfWeek
            val matchingEntries = timetableDaySlots.count { it == dayOfWeek }
            totalClasses += matchingEntries
            currentDate = currentDate.plusDays(1)
        }

        return totalClasses
    }
    
    /**
     * Get the configured class end date
     * @return The end date if configured, null otherwise
     */
    fun getConfiguredEndDate(): LocalDate? {
        return classEndDate
    }
}

