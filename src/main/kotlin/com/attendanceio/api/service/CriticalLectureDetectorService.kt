package com.attendanceio.api.service

import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

@Service
class CriticalLectureDetectorService(
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val classCalculationService: ClassCalculationService
) {
    private val logger = LoggerFactory.getLogger(CriticalLectureDetectorService::class.java)

    fun isCriticalLecture(studentId: Long, subjectId: Long): Boolean {
        logger.debug("Checking if lecture is critical: studentId=$studentId, subjectId=$subjectId")

        val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            ?: return false

        val minimumCriteria = studentSubject.minimumCriteria ?: return false

        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) return false
        val currentSemesterId = activeSemesters.first().id ?: return false

        val subjectTimetableEntries = studentTimetableRepositoryAppAction
            .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
            .filter { it.subject?.id == subjectId }

        if (subjectTimetableEntries.isEmpty()) return false

        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))

        val allAttendanceRecords = attendanceRepositoryAppAction
            .findByStudentIdAndSubjectId(studentId, subjectId)
            .filter { it.lectureDate != null && !it.lectureDate!!.isAfter(today) }

        val customTimeClassesCount = allAttendanceRecords.count {
            it.customStartTime != null && it.customEndTime != null
        }

        val slotBasedClassesCount = allAttendanceRecords.count {
            it.timeSlot != null && it.customStartTime == null && it.customEndTime == null
        }

        val datesWithCustomOrSlotClasses = allAttendanceRecords
            .filter { (it.customStartTime != null && it.customEndTime != null) || it.timeSlot != null }
            .mapNotNull { it.lectureDate }
            .toSet()

        val timetableClassesCount = classCalculationService.countScheduledClassesExcludingDates(
            subjectTimetableEntries, today, datesWithCustomOrSlotClasses
        )

        val totalCancelledCount = allAttendanceRecords.count { it.status == AttendanceStatus.CANCELLED }

        val calculatedTotal = maxOf(
            0,
            customTimeClassesCount + slotBasedClassesCount + timetableClassesCount - totalCancelledCount
        )
        val actualAttendanceMin = allAttendanceRecords.count { it.status != AttendanceStatus.CANCELLED }
        val totalClasses = maxOf(calculatedTotal, actualAttendanceMin)

        if (totalClasses == 0) {
            logger.debug("Total classes is 0 for studentId=$studentId, subjectId=$subjectId")
            return false
        }

        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        val subjectStats = attendanceResults.find { it.subjectId == subjectId } ?: return false

        val totalPresent = subjectStats.basePresent + subjectStats.presentAfterCutoff
        val percentage = (totalPresent.toDouble() / totalClasses) * 100
        val isCritical = percentage < minimumCriteria

        logger.info(
            "Critical check: studentId=$studentId, subjectId=$subjectId — " +
            "attendance=${"%.1f".format(percentage)}%, minimum=$minimumCriteria%, isCritical=$isCritical " +
            "(totalClasses=$totalClasses, cancelled=$totalCancelledCount)"
        )

        return isCritical
    }
}
