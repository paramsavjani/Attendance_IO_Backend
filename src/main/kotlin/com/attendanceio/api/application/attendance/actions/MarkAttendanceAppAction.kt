package com.attendanceio.api.application.attendance.actions

import com.attendanceio.api.model.attendance.AttendanceSource
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.DMAttendance
import com.attendanceio.api.model.attendance.MarkAttendanceRequest
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.TimeSlotRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Component
class MarkAttendanceAppAction(
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val timeSlotRepository: TimeSlotRepository
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @Transactional
    @CacheEvict(value = ["analytics"], allEntries = true)
    fun execute(student: DMStudent, request: MarkAttendanceRequest): DMAttendance {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")

        val subjectId = request.subjectId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid subject ID: ${request.subjectId}")

        val lectureDate = try {
            LocalDate.parse(request.lectureDate)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date format: ${request.lectureDate}. Expected format: yyyy-MM-dd")
        }

        val status = when (request.status.uppercase()) {
            "PRESENT" -> AttendanceStatus.PRESENT
            "ABSENT" -> AttendanceStatus.ABSENT
            "LEAVE" -> AttendanceStatus.LEAVE
            "CANCELLED" -> AttendanceStatus.CANCELLED
            else -> throw IllegalArgumentException("Invalid status: ${request.status}. Must be 'present', 'absent', 'leave', or 'cancelled'")
        }

        val today = LocalDate.now()
        if (lectureDate.isAfter(today) && status != AttendanceStatus.CANCELLED) {
            throw IllegalArgumentException("For future dates, you can only mark lectures as 'cancelled'. Cannot mark as 'present' or 'absent'.")
        }

        val subject = subjectRepositoryAppAction.findById(subjectId)
            ?: throw IllegalArgumentException("Subject not found: ${request.subjectId}")

        val hasTimeSlot = request.timeSlot != null
        val hasCustomTimes = request.startTime != null && request.endTime != null

        if (hasTimeSlot && hasCustomTimes) {
            throw IllegalArgumentException("Cannot provide both timeSlot and custom times")
        }

        // Treat as extra class when flag is true OR when no time info — client may omit flag due to JSON binding
        val treatAsExtraClass = request.isExtraClass || (!hasTimeSlot && !hasCustomTimes)
        val existingAttendance = if (treatAsExtraClass) {
            val extraClassRecords = attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndIsExtraClass(
                studentId, subjectId, lectureDate, true
            ).filter {
                it.timeSlot == null && it.customStartTime == null && it.customEndTime == null
            }.sortedBy { it.id }
            val idx = request.extraClassIndex ?: 0
            if (idx in extraClassRecords.indices) extraClassRecords[idx] else null
        } else {
            // Try exact time match first, then fall back to backward-compatibility records (no time info)
            when {
                hasTimeSlot -> {
                    val slotId = (request.timeSlot!! + 1).toShort()
                    attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndTimeSlotId(
                        studentId, subjectId, lectureDate, slotId
                    ) ?: attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDate(
                        studentId, subjectId, lectureDate
                    )?.takeIf { it.timeSlot == null && !it.isExtraClass }
                }
                hasCustomTimes -> {
                    val startTime = try { LocalTime.parse(request.startTime, timeFormatter) }
                    catch (e: Exception) { throw IllegalArgumentException("Invalid start time format: ${request.startTime}. Use HH:mm format") }
                    val endTime = try { LocalTime.parse(request.endTime, timeFormatter) }
                    catch (e: Exception) { throw IllegalArgumentException("Invalid end time format: ${request.endTime}. Use HH:mm format") }
                    attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndCustomStartTimeAndCustomEndTime(
                        studentId, subjectId, lectureDate, startTime, endTime
                    ) ?: attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDate(
                        studentId, subjectId, lectureDate
                    )?.takeIf { it.timeSlot == null && it.customStartTime == null && it.customEndTime == null && !it.isExtraClass }
                }
                else -> attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDate(
                    studentId, subjectId, lectureDate
                )?.takeIf { !it.isExtraClass }
            }
        }

        return if (existingAttendance != null) {
            existingAttendance.status = status
            existingAttendance.sourceId = AttendanceSource.STUDENT
            // Upgrade backward-compatibility records (no time info) to include time when provided
            if (!treatAsExtraClass) {
                if (hasTimeSlot && existingAttendance.timeSlot == null) {
                    val slotId = (request.timeSlot!! + 1).toShort()
                    existingAttendance.timeSlot = timeSlotRepository.findById(slotId).orElse(null)
                    existingAttendance.customStartTime = null
                    existingAttendance.customEndTime = null
                } else if (hasCustomTimes && existingAttendance.customStartTime == null) {
                    existingAttendance.timeSlot = null
                    existingAttendance.customStartTime = LocalTime.parse(request.startTime, timeFormatter)
                    existingAttendance.customEndTime = LocalTime.parse(request.endTime, timeFormatter)
                }
            }
            if (treatAsExtraClass) existingAttendance.isExtraClass = true
            attendanceRepositoryAppAction.save(existingAttendance)
        } else {
            DMAttendance().apply {
                this.student = student
                this.subject = subject
                this.lectureDate = lectureDate
                this.status = status
                this.sourceId = AttendanceSource.STUDENT
                this.isExtraClass = treatAsExtraClass
                if (hasTimeSlot) {
                    val slotId = (request.timeSlot!! + 1).toShort()
                    this.timeSlot = timeSlotRepository.findById(slotId).orElse(null)
                    this.customStartTime = null
                    this.customEndTime = null
                } else if (hasCustomTimes) {
                    this.timeSlot = null
                    this.customStartTime = LocalTime.parse(request.startTime, timeFormatter)
                    this.customEndTime = LocalTime.parse(request.endTime, timeFormatter)
                }
            }.let { attendanceRepositoryAppAction.save(it) }
        }
    }
}
