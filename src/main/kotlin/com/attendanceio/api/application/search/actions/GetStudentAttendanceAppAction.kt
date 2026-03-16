package com.attendanceio.api.application.search.actions

import com.attendanceio.api.application.search.adapters.StudentAttendanceAdapter
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.DMAttendance
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTutorialTimetableRepositoryAppAction
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime

@Component
class GetStudentAttendanceAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val studentTutorialTimetableRepositoryAppAction: StudentTutorialTimetableRepositoryAppAction,
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

            val present = subjectLectureAttendance.count { it.status == AttendanceStatus.PRESENT }
            val absent = subjectLectureAttendance.count { it.status == AttendanceStatus.ABSENT }
            val leave = subjectLectureAttendance.count { it.status == AttendanceStatus.LEAVE }

            result.subjectId to StudentAttendanceAdapter.SubjectAttendanceOverride(
                present = present,
                absent = absent,
                leave = leave,
                // Keep API invariant expected by UI: total must match present + absent.
                total = present + absent
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
}
