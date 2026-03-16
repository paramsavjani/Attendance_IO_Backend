package com.attendanceio.api.application.search.actions

import com.attendanceio.api.application.search.adapters.StudentAttendanceAdapter
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.DMAttendance
import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTutorialTimetableRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
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
    private val classCalculationService: ClassCalculationService,
    private val studentAttendanceAdapter: StudentAttendanceAdapter
) {
    fun execute(studentId: Long): StudentAttendanceResponse {
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")

        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        if (attendanceResults.isEmpty()) {
            return studentAttendanceAdapter.toResponse(
                studentId = studentId,
                studentName = student.name ?: "",
                rollNumber = student.sid,
                studentPictureUrl = student.pictureUrl,
                attendanceResults = emptyList()
            )
        }

        val semesterIds = attendanceResults.map { it.semesterId }.distinct()

        val allTimetableEntries = semesterIds.flatMap { semesterId ->
            studentTimetableRepositoryAppAction.findByStudentIdAndSemesterIdWithDetails(studentId, semesterId)
        }

        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)
        val today = LocalDate.now()

        val subjectSemesterMap = attendanceResults.associate { it.subjectId to it.semesterId }
        val attendanceBySemester = allAttendanceRecords
            .mapNotNull { attendance ->
                val subjectId = attendance.subject?.id ?: return@mapNotNull null
                val semesterId = subjectSemesterMap[subjectId] ?: return@mapNotNull null
                semesterId to attendance
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        val timetableBySemester = allTimetableEntries.groupBy { it.semester?.id }

        val lectureAttendanceBySemester = semesterIds.associateWith { semesterId ->
            val labTutorialAttendanceKeys = loadLabTutorialAttendanceKeys(studentId, semesterId)
            attendanceBySemester[semesterId].orEmpty()
                .filter { isLectureAttendance(it, labTutorialAttendanceKeys) }
        }

        val subjectOverrides = attendanceResults.associate { result ->
            val semesterLectureAttendance = lectureAttendanceBySemester[result.semesterId].orEmpty()
            val subjectLectureAttendance = semesterLectureAttendance.filter {
                it.subject?.id == result.subjectId &&
                    it.lectureDate != null &&
                    !it.lectureDate!!.isAfter(today)
            }

            val subjectTimetableEntries = timetableBySemester[result.semesterId]
                .orEmpty()
                .filter { it.subject?.id == result.subjectId }

            val total = calculateTotalClasses(
                subjectAttendanceRecords = subjectLectureAttendance,
                subjectTimetableEntries = subjectTimetableEntries,
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

        return studentAttendanceAdapter.toResponse(
            studentId = studentId,
            studentName = student.name ?: "",
            rollNumber = student.sid,
            studentPictureUrl = student.pictureUrl,
            attendanceResults = attendanceResults,
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

    private data class AttendanceTimeKey(
        val subjectId: Long,
        val startTime: LocalTime,
        val endTime: LocalTime
    )

    private fun toTimeSlotPair(startTime: LocalTime?, endTime: LocalTime?): Pair<LocalTime, LocalTime>? =
        if (startTime != null && endTime != null) Pair(startTime, endTime) else null

    private fun loadLabTutorialAttendanceKeys(
        studentId: Long,
        semesterId: Long
    ): Set<AttendanceTimeKey> {
        val labKeys = studentLabTimetableRepositoryAppAction
            .findByStudentIdAndSemesterId(studentId, semesterId)
            .mapNotNull { entry ->
                val subjectId = entry.subject?.id ?: return@mapNotNull null
                val (startTime, endTime) = toTimeSlotPair(
                    entry.customStartTime ?: entry.slot?.startTime,
                    entry.customEndTime ?: entry.slot?.endTime
                ) ?: return@mapNotNull null
                AttendanceTimeKey(subjectId, startTime, endTime)
            }

        val tutorialKeys = studentTutorialTimetableRepositoryAppAction
            .findByStudentIdAndSemesterId(studentId, semesterId)
            .mapNotNull { entry ->
                val subjectId = entry.subject?.id ?: return@mapNotNull null
                val (startTime, endTime) = toTimeSlotPair(
                    entry.customStartTime ?: entry.slot?.startTime,
                    entry.customEndTime ?: entry.slot?.endTime
                ) ?: return@mapNotNull null
                AttendanceTimeKey(subjectId, startTime, endTime)
            }

        return (labKeys + tutorialKeys).toSet()
    }

    private fun isLectureAttendance(
        attendance: DMAttendance,
        labTutorialAttendanceKeys: Set<AttendanceTimeKey>
    ): Boolean {
        val subjectId = attendance.subject?.id ?: return false
        val (startTime, endTime) = toTimeSlotPair(
            attendance.customStartTime ?: attendance.timeSlot?.startTime,
            attendance.customEndTime ?: attendance.timeSlot?.endTime
        ) ?: return true

        return AttendanceTimeKey(subjectId, startTime, endTime) !in labTutorialAttendanceKeys
    }

    private fun calculateTotalClasses(
        subjectAttendanceRecords: List<DMAttendance>,
        subjectTimetableEntries: List<DMStudentTimetable>,
        endDate: LocalDate,
        includeExtraClasses: Boolean
    ): Int {
        val computedTotalClasses = classCalculationService.calculateTotalClasses(subjectTimetableEntries, endDate)
        val filteredAttendanceRecords = subjectAttendanceRecords.filter {
            it.lectureDate != null && !it.lectureDate!!.isAfter(endDate)
        }

        val customTimeClassesCount = filteredAttendanceRecords.count {
            it.customStartTime != null &&
                it.customEndTime != null &&
                !it.isExtraClass
        }

        val slotBasedClassesCount = filteredAttendanceRecords.count {
            it.timeSlot != null &&
                it.customStartTime == null &&
                it.customEndTime == null &&
                !it.isExtraClass
        }

        val extraClassesCount = if (includeExtraClasses) {
            filteredAttendanceRecords.count {
                it.isExtraClass &&
                    it.timeSlot == null &&
                    it.customStartTime == null &&
                    it.customEndTime == null
            }
        } else {
            0
        }

        val datesWithCustomOrSlotClasses = filteredAttendanceRecords
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

        val totalCancelledCount = filteredAttendanceRecords.count { it.status == AttendanceStatus.CANCELLED }

        val calculatedTotal = maxOf(
            0,
            customTimeClassesCount + slotBasedClassesCount + extraClassesCount + timetableClassesCount - totalCancelledCount
        )

        val actualAttendanceMin = filteredAttendanceRecords.count { it.status != AttendanceStatus.CANCELLED }
        return maxOf(0, maxOf(calculatedTotal, actualAttendanceMin))
    }
}
