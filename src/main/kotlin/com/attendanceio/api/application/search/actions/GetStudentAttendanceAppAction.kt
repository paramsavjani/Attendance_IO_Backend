package com.attendanceio.api.application.search.actions

import com.attendanceio.api.application.search.adapters.StudentAttendanceAdapter
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate

@Component
class GetStudentAttendanceAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val classCalculationService: ClassCalculationService,
    private val studentAttendanceAdapter: StudentAttendanceAdapter
) {
    fun execute(studentId: Long): StudentAttendanceResponse {
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")

        // Single database query that does all calculations
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        
        // Get unique semester IDs from attendance results
        val semesterIds = attendanceResults.map { it.semesterId }.distinct()
        
        // Get all timetable entries for the student (across all semesters) with details loaded
        val allTimetableEntries = semesterIds.flatMap { semesterId ->
            studentTimetableRepositoryAppAction.findByStudentIdAndSemesterIdWithDetails(studentId, semesterId)
        }
        
        // Get all attendance records to count cancelled classes
        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)
        
        // Calculate computed total classes including today for each subject
        val today = LocalDate.now()
        val computedTotals = attendanceResults.associate { result ->
            // Get timetable entries for this subject
            val subjectTimetableEntries = allTimetableEntries.filter { 
                it.subject?.id == result.subjectId 
            }
            
            // Calculate total expected classes based on timetable (including today)
            val computedTotalClasses = classCalculationService.calculateTotalClasses(
                subjectTimetableEntries,
                today
            )
            
            // Step 1: Calculate total custom time classes (count ALL, including cancelled)
            val customTimeClassesCount = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(today) &&
                    it.customStartTime != null && 
                    it.customEndTime != null
                }
                .size
            
            // Step 2: Calculate total slot-based classes (count ALL, including cancelled)
            // Slot-based classes are those with time_slot_id (but no custom times)
            val slotBasedClassesCount = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(today) &&
                    it.timeSlot != null &&
                    it.customStartTime == null && 
                    it.customEndTime == null
                }
                .size
            
            // Get dates that have custom time classes or slot-based classes (these replace timetable classes for those dates)
            val datesWithCustomOrSlotClasses = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(today) &&
                    ((it.customStartTime != null && it.customEndTime != null) || it.timeSlot != null)
                }
                .mapNotNull { it.lectureDate }
                .toSet()
            
            // Step 3: Calculate total classes from timetable schedule (excluding dates with custom/slot classes)
            val timetableClassesCount = if (computedTotalClasses > 0 && subjectTimetableEntries.isNotEmpty()) {
                val startDate = classCalculationService.getConfiguredStartDate()
                if (startDate != null && !today.isBefore(startDate)) {
                    val timetableDaySlots = subjectTimetableEntries.mapNotNull { entry ->
                        val dayName = entry.day?.name?.uppercase()
                        when (dayName) {
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
                    
                    var timetableCount = 0
                    var currentDate: LocalDate = startDate
                    while (!currentDate.isAfter(today)) {
                        if (currentDate !in datesWithCustomOrSlotClasses) {
                            val dayOfWeek = currentDate.dayOfWeek
                            val matchingEntries = timetableDaySlots.count { it == dayOfWeek }
                            timetableCount += matchingEntries
                        }
                        currentDate = currentDate.plusDays(1)
                    }
                    timetableCount
                } else {
                    0
                }
            } else {
                0
            }
            
            // Step 4: Count all cancelled classes (custom time, slot-based, and timetable)
            val totalCancelledCount = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(today) &&
                    it.status == AttendanceStatus.CANCELLED
                }
                .size
            
            // Step 5: Calculate total classes
            // Count ALL actual attendance records (present + absent + cancelled) - this is the base
            val allAttendanceRecordsCount = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(today)
                }
                .size
            
            // Add timetable classes for dates that don't have any attendance records
            val attendanceDates = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(today)
                }
                .mapNotNull { it.lectureDate }
                .toSet()
            
            val timetableClassesWithoutAttendance = if (computedTotalClasses > 0 && subjectTimetableEntries.isNotEmpty()) {
                val startDate = classCalculationService.getConfiguredStartDate()
                if (startDate != null && !today.isBefore(startDate)) {
                    val timetableDaySlots = subjectTimetableEntries.mapNotNull { entry ->
                        val dayName = entry.day?.name?.uppercase()
                        when (dayName) {
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
                    
                    var timetableCount = 0
                    var currentDate: LocalDate = startDate
                    while (!currentDate.isAfter(today)) {
                        if (currentDate !in attendanceDates) {
                            val dayOfWeek = currentDate.dayOfWeek
                            val matchingEntries = timetableDaySlots.count { it == dayOfWeek }
                            timetableCount += matchingEntries
                        }
                        currentDate = currentDate.plusDays(1)
                    }
                    timetableCount
                } else {
                    0
                }
            } else {
                0
            }
            
            // Calculate total using: custom + slot + timetable - cancelled
            val calculatedTotal = maxOf(0, customTimeClassesCount + slotBasedClassesCount + timetableClassesCount - totalCancelledCount)
            
            // Count actual attendance records (present + absent, excluding cancelled) as minimum
            val actualAttendanceMin = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(today) &&
                    it.status != AttendanceStatus.CANCELLED
                }
                .size
            
            // Total = max(calculated total, actual attendance minimum)
            // This ensures we never show 0 when there are actual attendance records
            val totalClasses = maxOf(calculatedTotal, actualAttendanceMin)
            
            result.subjectId to maxOf(0, totalClasses) // Ensure total is not negative
        }
        
        // Use adapter to convert to response model
        return studentAttendanceAdapter.toResponse(
            studentId = studentId,
            studentName = student.name ?: "",
            rollNumber = student.sid,
            studentPictureUrl = student.pictureUrl,
            attendanceResults = attendanceResults,
            computedTotals = computedTotals
        )
    }
}
