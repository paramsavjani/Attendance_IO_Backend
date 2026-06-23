package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.AttendanceCalculationResult
import com.attendanceio.api.model.attendance.DMAttendance
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate
import java.time.LocalTime

@Component
class AttendanceRepositoryAppAction(
    private val attendanceRepository: AttendanceRepository
) {
    fun findByStudentIdAndSubjectId(studentId: Long, subjectId: Long): List<DMAttendance> =
        attendanceRepository.findByStudentIdAndSubjectId(studentId, subjectId)

    fun findByStudentId(studentId: Long): List<DMAttendance> =
        attendanceRepository.findByStudentId(studentId)

    fun findByStudentIdAndLectureDate(studentId: Long, lectureDate: LocalDate): List<DMAttendance> =
        attendanceRepository.findByStudentIdAndLectureDate(studentId, lectureDate)

    fun findBySubjectId(subjectId: Long): List<DMAttendance> =
        attendanceRepository.findBySubjectId(subjectId)

    fun findByStudentIdAndSubjectIdAndLectureDate(studentId: Long, subjectId: Long, lectureDate: LocalDate): DMAttendance? =
        attendanceRepository.findByStudentIdAndSubjectIdAndLectureDate(studentId, subjectId, lectureDate)

    fun findByStudentIdAndSubjectIdAndLectureDateAndTimeSlotId(
        studentId: Long, subjectId: Long, lectureDate: LocalDate, timeSlotId: Short
    ): DMAttendance? =
        attendanceRepository.findByStudentIdAndSubjectIdAndLectureDateAndTimeSlotId(studentId, subjectId, lectureDate, timeSlotId)

    fun findByStudentIdAndSubjectIdAndLectureDateAndCustomStartTimeAndCustomEndTime(
        studentId: Long, subjectId: Long, lectureDate: LocalDate, customStartTime: LocalTime, customEndTime: LocalTime
    ): DMAttendance? =
        attendanceRepository.findByStudentIdAndSubjectIdAndLectureDateAndCustomStartTimeAndCustomEndTime(
            studentId, subjectId, lectureDate, customStartTime, customEndTime
        )

    fun findByStudentIdAndSubjectIdAndLectureDateAndIsExtraClass(
        studentId: Long, subjectId: Long, lectureDate: LocalDate, isExtraClass: Boolean
    ): List<DMAttendance> =
        attendanceRepository.findByStudentIdAndSubjectIdAndLectureDateAndIsExtraClass(studentId, subjectId, lectureDate, isExtraClass)

    fun findByStudentIdAndLectureDateBetween(studentId: Long, startDate: LocalDate, endDate: LocalDate): List<DMAttendance> =
        attendanceRepository.findByStudentIdAndLectureDateBetween(studentId, startDate, endDate)

    fun findByStudentIdAndSubjectIdAndLectureDateAfter(studentId: Long, subjectId: Long, lectureDate: LocalDate): List<DMAttendance> =
        attendanceRepository.findByStudentIdAndSubjectIdAndLectureDateAfter(studentId, subjectId, lectureDate)

    fun calculateStudentAttendanceBySubject(studentId: Long): List<AttendanceCalculationResult> =
        attendanceRepository.calculateStudentAttendanceBySubject(studentId).map { row ->
            AttendanceCalculationResult(
                subjectId = (row[0] as Number).toLong(),
                subjectCode = row[1] as String,
                subjectName = row[2] as String,
                subjectColor = row[3] as? String ?: "#3B82F6",
                semesterId = (row[4] as Number).toLong(),
                semesterYear = (row[5] as Number).toInt(),
                semesterType = row[6] as String,
                basePresent = (row[7] as Number).toInt(),
                baseAbsent = (row[8] as Number).toInt(),
                baseTotal = (row[9] as Number).toInt(),
                presentAfterCutoff = (row[10] as Number).toInt(),
                absentAfterCutoff = (row[11] as Number).toInt(),
                leaveAfterCutoff = (row[12] as Number).toInt(),
                totalAfterCutoff = (row[13] as Number).toInt(),
                baseCutoffDate = when (val v = row[14]) {
                    is LocalDate -> v
                    is Date -> v.toLocalDate()
                    else -> null
                }
            )
        }

    fun save(attendance: DMAttendance): DMAttendance =
        attendanceRepository.save(attendance)

    fun delete(attendance: DMAttendance) =
        attendanceRepository.delete(attendance)

    @Transactional
    fun deleteAllByStudentIdAndSubjectIds(studentId: Long, subjectIds: List<Long>) {
        if (subjectIds.isNotEmpty()) {
            attendanceRepository.deleteAllByStudentIdAndSubjectIdIn(studentId, subjectIds)
        }
    }
}
