package com.attendanceio.api.application.attendance.actions

import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.MyAttendanceResponse
import com.attendanceio.api.model.attendance.SubjectStatsResponse
import com.attendanceio.api.model.attendance.TodayAttendanceRecord
import com.attendanceio.api.model.timetable.DMStudentLabTimetable
import com.attendanceio.api.model.timetable.DMStudentTutorialTimetable
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTutorialTimetableRepositoryAppAction
import com.attendanceio.api.service.AttendanceCalculationService
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime

@Component
class GetLabTutorialAttendanceAppAction(
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val studentTutorialTimetableRepositoryAppAction: StudentTutorialTimetableRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val classCalculationService: ClassCalculationService,
    private val attendanceCalculationService: AttendanceCalculationService
) {
    fun execute(studentId: Long): MyAttendanceResponse {
        val currentSemester = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()
            ?: return MyAttendanceResponse(emptyList(), emptyList())

        val labTimetable = studentLabTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)
        val tutorialTimetable = studentTutorialTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)

        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)

        val subjectIds = (labTimetable.mapNotNull { it.subject?.id } +
                tutorialTimetable.mapNotNull { it.subject?.id }).distinct()

        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
            .filter { it.subjectId in subjectIds }

        val today = LocalDate.now()
        val endDate = classCalculationService.getConfiguredEndDate() ?: today

        // Map of subjectId -> set of (start, end) lab/tutorial time slots
        val labTutSlotsBySubject = buildLabTutSlotsBySubject(labTimetable, tutorialTimetable)
        val allLabTutSlots = labTutSlotsBySubject.values.flatten().toSet()

        val subjectStats = attendanceResults.map { result ->
            val subjectId = result.subjectId

            val subjectSlots = labTutSlotsBySubject[subjectId].orEmpty()
            val labTutAttendance = allAttendanceRecords.filter { att ->
                att.subject?.id == subjectId &&
                att.customStartTime != null && att.customEndTime != null &&
                Pair(att.customStartTime!!, att.customEndTime!!) in subjectSlots
            }

            val present = labTutAttendance.count { it.status == AttendanceStatus.PRESENT }
            val absent = labTutAttendance.count { it.status == AttendanceStatus.ABSENT }

            val subjectLabEntries: List<Any> =
                labTimetable.filter { it.subject?.id == subjectId }.map { it } +
                tutorialTimetable.filter { it.subject?.id == subjectId }.map { it }
            val timetableDays = subjectLabEntries.mapNotNull { entry ->
                val dayName = when (entry) {
                    is DMStudentLabTimetable -> entry.day?.name
                    is DMStudentTutorialTimetable -> entry.day?.name
                    else -> null
                }
                ClassCalculationService.parseDayOfWeek(dayName)
            }

            val computedTotal = classCalculationService.calculateTotalClassesFromDays(timetableDays, today)
            val computedTotalEnd = classCalculationService.calculateTotalClassesFromDays(timetableDays, endDate)

            val cancelledToday = labTutAttendance.count {
                it.lectureDate != null && !it.lectureDate!!.isAfter(today) && it.status == AttendanceStatus.CANCELLED
            }
            val cancelledToEnd = labTutAttendance.count {
                it.lectureDate != null && !it.lectureDate!!.isAfter(endDate) && it.status == AttendanceStatus.CANCELLED
            }

            val totalClasses = maxOf(0, computedTotal - cancelledToday)
            val totalUntilEnd = maxOf(totalClasses, maxOf(0, computedTotalEnd - cancelledToEnd))

            val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            val minRequired = studentSubject?.minimumCriteria ?: 75
            val percentage = attendanceCalculationService.calculatePercentage(present, totalClasses)
            val classesNeeded = attendanceCalculationService.calculateClassesNeeded(present, totalClasses, minRequired)
            val bunkableClasses = attendanceCalculationService.calculateBunkableClasses(present, totalClasses, totalUntilEnd, minRequired)

            SubjectStatsResponse(
                subjectId = subjectId.toString(),
                present = present,
                absent = absent,
                total = totalClasses,
                totalUntilEndDate = totalUntilEnd,
                percentage = percentage,
                classesNeeded = classesNeeded,
                bunkableClasses = bunkableClasses
            )
        }

        val todayLabTutAttendance = allAttendanceRecords
            .filter { it.lectureDate == today }
            .filter { att ->
                val sid = att.subject?.id ?: return@filter false
                if (sid !in subjectIds) return@filter false
                att.customStartTime != null && att.customEndTime != null &&
                Pair(att.customStartTime!!, att.customEndTime!!) in allLabTutSlots
            }
            .map { att ->
                TodayAttendanceRecord(
                    attendanceId = att.id,
                    subjectId = att.subject?.id?.toString() ?: "",
                    lectureDate = att.lectureDate?.toString() ?: "",
                    status = att.status.name.lowercase(),
                    timeSlot = att.timeSlot?.id?.toInt()?.minus(1),
                    startTime = att.customStartTime?.toString(),
                    endTime = att.customEndTime?.toString()
                )
            }

        return MyAttendanceResponse(subjectStats = subjectStats, todayAttendance = todayLabTutAttendance)
    }

    private fun buildLabTutSlotsBySubject(
        labTimetable: List<DMStudentLabTimetable>,
        tutorialTimetable: List<DMStudentTutorialTimetable>
    ): Map<Long, Set<Pair<LocalTime, LocalTime>>> {
        val map = mutableMapOf<Long, MutableSet<Pair<LocalTime, LocalTime>>>()
        labTimetable.forEach { entry ->
            val subjectId = entry.subject?.id ?: return@forEach
            val start = entry.customStartTime ?: entry.slot?.startTime ?: return@forEach
            val end = entry.customEndTime ?: entry.slot?.endTime ?: return@forEach
            map.getOrPut(subjectId) { mutableSetOf() }.add(Pair(start, end))
        }
        tutorialTimetable.forEach { entry ->
            val subjectId = entry.subject?.id ?: return@forEach
            val start = entry.customStartTime ?: entry.slot?.startTime ?: return@forEach
            val end = entry.customEndTime ?: entry.slot?.endTime ?: return@forEach
            map.getOrPut(subjectId) { mutableSetOf() }.add(Pair(start, end))
        }
        return map
    }
}
