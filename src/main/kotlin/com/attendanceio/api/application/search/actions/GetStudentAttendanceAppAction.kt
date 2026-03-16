package com.attendanceio.api.application.search.actions

import com.attendanceio.api.application.search.adapters.StudentAttendanceAdapter
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.DMAttendance
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTutorialTimetableRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
import com.attendanceio.api.model.timetable.DMStudentTimetable
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Component
class GetStudentAttendanceAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val studentTutorialTimetableRepositoryAppAction: StudentTutorialTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
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
        val today = LocalDate.now()

        // Keep existing fallback totals for non-active semesters.
        // Calculate computed total classes including today for each subject
        val computedTotals = attendanceResults.associate { result ->
            // Get timetable entries for this subject
            val subjectTimetableEntries = allTimetableEntries.filter { 
                it.subject?.id == result.subjectId 
            }

            result.subjectId to calculateTotalClasses(
                attendanceRecords = allAttendanceRecords,
                subjectId = result.subjectId,
                subjectTimetableEntries = subjectTimetableEntries,
                endDate = today,
                includeExtraClasses = false
            )
        }

        val currentSemesterId = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()?.id
        val subjectOverrides = if (currentSemesterId != null) {
            val currentSemesterTimetable = allTimetableEntries.filter { it.semester?.id == currentSemesterId }

            val labTimeSlots = studentLabTimetableRepositoryAppAction
                .findByStudentIdAndSemesterId(studentId, currentSemesterId)
                .mapNotNull { toTimeSlotPair(it.customStartTime ?: it.slot?.startTime, it.customEndTime ?: it.slot?.endTime) }

            val tutorialTimeSlots = studentTutorialTimetableRepositoryAppAction
                .findByStudentIdAndSemesterId(studentId, currentSemesterId)
                .mapNotNull { toTimeSlotPair(it.customStartTime ?: it.slot?.startTime, it.customEndTime ?: it.slot?.endTime) }

            val labTutorialTimeSlots = (labTimeSlots + tutorialTimeSlots).toSet()

            // Mirror homepage behavior: remove attendance entries matching lab/tutorial time slots.
            val lectureOnlyAttendanceRecords = allAttendanceRecords.filter { attendance ->
                if (attendance.customStartTime != null && attendance.customEndTime != null) {
                    val timePair = Pair(attendance.customStartTime!!, attendance.customEndTime!!)
                    timePair !in labTutorialTimeSlots
                } else {
                    true
                }
            }

            attendanceResults
                .filter { it.semesterId == currentSemesterId }
                .associate { result ->
                    val subjectLectureAttendance = lectureOnlyAttendanceRecords.filter {
                        it.subject?.id == result.subjectId &&
                            it.lectureDate != null &&
                            !it.lectureDate!!.isAfter(today)
                    }

                    val total = calculateTotalClasses(
                        attendanceRecords = lectureOnlyAttendanceRecords,
                        subjectId = result.subjectId,
                        subjectTimetableEntries = currentSemesterTimetable.filter { it.subject?.id == result.subjectId },
                        endDate = today,
                        includeExtraClasses = true
                    )

                    result.subjectId to StudentAttendanceAdapter.SubjectAttendanceOverride(
                        present = subjectLectureAttendance.count { it.status == AttendanceStatus.PRESENT },
                        absent = subjectLectureAttendance.count { it.status == AttendanceStatus.ABSENT },
                        leave = subjectLectureAttendance.count { it.status == AttendanceStatus.LEAVE },
                        total = total
                    )
                }
        } else {
            emptyMap()
        }

        // Use adapter to convert to response model
        return studentAttendanceAdapter.toResponse(
            studentId = studentId,
            studentName = student.name ?: "",
            rollNumber = student.sid,
            studentPictureUrl = student.pictureUrl,
            attendanceResults = attendanceResults,
            computedTotals = computedTotals,
            subjectOverrides = subjectOverrides
        )
    }

    private fun toDayOfWeek(dayName: String?): DayOfWeek? = when (dayName?.uppercase()) {
        "MONDAY" -> DayOfWeek.MONDAY
        "TUESDAY" -> DayOfWeek.TUESDAY
        "WEDNESDAY" -> DayOfWeek.WEDNESDAY
        "THURSDAY" -> DayOfWeek.THURSDAY
        "FRIDAY" -> DayOfWeek.FRIDAY
        "SATURDAY" -> DayOfWeek.SATURDAY
        "SUNDAY" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun toTimeSlotPair(startTime: LocalTime?, endTime: LocalTime?): Pair<LocalTime, LocalTime>? {
        return if (startTime != null && endTime != null) Pair(startTime, endTime) else null
    }

    private fun calculateTotalClasses(
        attendanceRecords: List<DMAttendance>,
        subjectId: Long,
        subjectTimetableEntries: List<DMStudentTimetable>,
        endDate: LocalDate,
        includeExtraClasses: Boolean
    ): Int {
        val computedTotalClasses = classCalculationService.calculateTotalClasses(subjectTimetableEntries, endDate)
        val subjectAttendanceRecords = attendanceRecords.filter {
            it.subject?.id == subjectId &&
                it.lectureDate != null &&
                !it.lectureDate!!.isAfter(endDate)
        }

        val customTimeClassesCount = subjectAttendanceRecords.count {
            it.customStartTime != null &&
                it.customEndTime != null &&
                !it.isExtraClass
        }

        val slotBasedClassesCount = subjectAttendanceRecords.count {
            it.timeSlot != null &&
                it.customStartTime == null &&
                it.customEndTime == null &&
                !it.isExtraClass
        }

        val extraClassesCount = if (includeExtraClasses) {
            subjectAttendanceRecords.count {
                it.isExtraClass &&
                    it.timeSlot == null &&
                    it.customStartTime == null &&
                    it.customEndTime == null
            }
        } else {
            0
        }

        val datesWithCustomOrSlotClasses = subjectAttendanceRecords
            .filter { (it.customStartTime != null && it.customEndTime != null) || it.timeSlot != null }
            .mapNotNull { it.lectureDate }
            .toSet()

        val timetableClassesCount = if (computedTotalClasses > 0 && subjectTimetableEntries.isNotEmpty()) {
            val startDate = classCalculationService.getConfiguredStartDate()
            if (startDate != null && !endDate.isBefore(startDate)) {
                val timetableDaySlots = subjectTimetableEntries.mapNotNull { toDayOfWeek(it.day?.name) }

                var timetableCount = 0
                var currentDate: LocalDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    if (currentDate !in datesWithCustomOrSlotClasses) {
                        timetableCount += timetableDaySlots.count { it == currentDate.dayOfWeek }
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

        val totalCancelledCount = subjectAttendanceRecords.count { it.status == AttendanceStatus.CANCELLED }

        val calculatedTotal = maxOf(
            0,
            customTimeClassesCount + slotBasedClassesCount + extraClassesCount + timetableClassesCount - totalCancelledCount
        )

        val actualAttendanceMin = subjectAttendanceRecords.count { it.status != AttendanceStatus.CANCELLED }
        return maxOf(0, maxOf(calculatedTotal, actualAttendanceMin))
    }
}
