package com.attendanceio.api.application.search.actions

import com.attendanceio.api.application.search.adapters.StudentAttendanceAdapter
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTutorialTimetableRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate

@Component
class GetStudentAttendanceAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val studentTutorialTimetableRepositoryAppAction: StudentTutorialTimetableRepositoryAppAction,
    private val classCalculationService: ClassCalculationService,
    private val studentAttendanceAdapter: StudentAttendanceAdapter
) {
    fun execute(studentId: Long): StudentAttendanceResponse {
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")

        // If official institute data exists for this student, return only that
        val officialRecords = instituteAttendanceRepositoryAppAction
            .findByStudentIdAndIsOfficial(studentId, true)
        if (officialRecords.isNotEmpty()) {
            return studentAttendanceAdapter.toOfficialResponse(
                studentId = studentId,
                studentName = student.name ?: "",
                rollNumber = student.sid,
                studentPictureUrl = student.pictureUrl,
                officialRecords = officialRecords
            )
        }

        // Fallback: use app attendance data (existing behavior)
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        
        // Get unique semester IDs from attendance results
        val semesterIds = attendanceResults.map { it.semesterId }.distinct()
        
        // Get all timetable entries for the student (across all semesters) with details loaded
        val allTimetableEntries = semesterIds.flatMap { semesterId ->
            studentTimetableRepositoryAppAction.findByStudentIdAndSemesterIdWithDetails(studentId, semesterId)
        }

        val labTutorialTimeSlotsBySubject = mutableMapOf<Long, MutableSet<Pair<java.time.LocalTime, java.time.LocalTime>>>()
        semesterIds.forEach { semesterId ->
            studentLabTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, semesterId).forEach { entry ->
                val subjectId = entry.subject?.id
                val startTime = entry.customStartTime ?: entry.slot?.startTime
                val endTime = entry.customEndTime ?: entry.slot?.endTime
                if (subjectId != null && startTime != null && endTime != null) {
                    labTutorialTimeSlotsBySubject
                        .getOrPut(subjectId) { mutableSetOf() }
                        .add(Pair(startTime, endTime))
                }
            }

            studentTutorialTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, semesterId).forEach { entry ->
                val subjectId = entry.subject?.id
                val startTime = entry.customStartTime ?: entry.slot?.startTime
                val endTime = entry.customEndTime ?: entry.slot?.endTime
                if (subjectId != null && startTime != null && endTime != null) {
                    labTutorialTimeSlotsBySubject
                        .getOrPut(subjectId) { mutableSetOf() }
                        .add(Pair(startTime, endTime))
                }
            }
        }
        
        // Get all attendance records to count cancelled classes
        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)
        val lectureOnlyAttendanceRecords = allAttendanceRecords.filter { attendance ->
            val subjectId = attendance.subject?.id ?: return@filter true
            if (attendance.customStartTime != null && attendance.customEndTime != null) {
                val subjectLabTutorialSlots = labTutorialTimeSlotsBySubject[subjectId].orEmpty()
                Pair(attendance.customStartTime!!, attendance.customEndTime!!) !in subjectLabTutorialSlots
            } else {
                true
            }
        }
        
        // Calculate computed total classes including today for each subject
        val today = LocalDate.now()
        val computedTotals = mutableMapOf<Long, Int>()
        val computedPresents = mutableMapOf<Long, Int>()
        attendanceResults.forEach { result ->
            // Get timetable entries for this subject
            val subjectTimetableEntries = allTimetableEntries.filter { 
                it.subject?.id == result.subjectId 
            }
            val subjectAllAttendance = allAttendanceRecords.filter {
                it.subject?.id == result.subjectId &&
                    it.lectureDate != null &&
                    !it.lectureDate!!.isAfter(today)
            }
            val subjectLectureAttendance = lectureOnlyAttendanceRecords.filter {
                it.subject?.id == result.subjectId &&
                    it.lectureDate != null &&
                    !it.lectureDate!!.isAfter(today)
            }
            val subjectHasLabTutorialConfig = labTutorialTimeSlotsBySubject[result.subjectId]?.isNotEmpty() == true

            if (subjectAllAttendance.isEmpty() && !subjectHasLabTutorialConfig) {
                return@forEach
            }
            
            // Calculate total expected classes based on timetable (including today)
            val computedTotalClasses = classCalculationService.calculateTotalClasses(
                subjectTimetableEntries,
                today
            )
            val lecturePresent = subjectLectureAttendance.count { it.status == AttendanceStatus.PRESENT }
            
            // Step 1: Calculate total custom time classes (count ALL, including cancelled)
            val customTimeClassesCount = subjectLectureAttendance.count {
                it.customStartTime != null &&
                    it.customEndTime != null &&
                    !it.isExtraClass
            }
            
            // Step 2: Calculate total slot-based classes (count ALL, including cancelled)
            // Slot-based classes are those with time_slot_id (but no custom times)
            val slotBasedClassesCount = subjectLectureAttendance.count {
                it.timeSlot != null &&
                    it.customStartTime == null &&
                    it.customEndTime == null &&
                    !it.isExtraClass
            }

            val extraClassesCount = subjectLectureAttendance.count {
                it.isExtraClass &&
                    it.timeSlot == null &&
                    it.customStartTime == null &&
                    it.customEndTime == null
            }
            
            // Get dates that have custom time classes or slot-based classes (these replace timetable classes for those dates)
            val datesWithCustomOrSlotClasses = subjectLectureAttendance
                .filter {
                    (it.customStartTime != null && it.customEndTime != null) || it.timeSlot != null
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
            val totalCancelledCount = subjectLectureAttendance.count {
                it.status == AttendanceStatus.CANCELLED
            }
            
            // Calculate total using: custom + slot + extra + timetable - cancelled
            val calculatedTotal = maxOf(0, customTimeClassesCount + slotBasedClassesCount + extraClassesCount + timetableClassesCount - totalCancelledCount)
            
            // Count actual attendance records (present + absent, excluding cancelled) as minimum
            val actualAttendanceMin = subjectLectureAttendance.count {
                it.status != AttendanceStatus.CANCELLED
            }
            
            // Total = max(calculated total, actual attendance minimum)
            // This ensures we never show 0 when there are actual attendance records
            val totalClasses = maxOf(calculatedTotal, actualAttendanceMin)
            computedTotals[result.subjectId] = maxOf(0, totalClasses)
            computedPresents[result.subjectId] = lecturePresent
        }
        
        // Use adapter to convert to response model
        return studentAttendanceAdapter.toResponse(
            studentId = studentId,
            studentName = student.name ?: "",
            rollNumber = student.sid,
            studentPictureUrl = student.pictureUrl,
            attendanceResults = attendanceResults,
            computedTotals = computedTotals,
            computedPresents = computedPresents
        )
    }
}
