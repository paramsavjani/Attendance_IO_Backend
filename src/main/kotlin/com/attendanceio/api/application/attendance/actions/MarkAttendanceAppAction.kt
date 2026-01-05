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
    @Transactional
    @CacheEvict(value = ["analytics"], allEntries = true)
    fun execute(student: DMStudent, request: MarkAttendanceRequest): DMAttendance {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        
        // Parse subject ID
        val subjectId = request.subjectId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid subject ID: ${request.subjectId}")
        
        // Parse lecture date
        val lectureDate = try {
            LocalDate.parse(request.lectureDate)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date format: ${request.lectureDate}. Expected format: yyyy-MM-dd")
        }
        
        // Parse status
        val status = when (request.status.uppercase()) {
            "PRESENT" -> AttendanceStatus.PRESENT
            "ABSENT" -> AttendanceStatus.ABSENT
            "LEAVE" -> AttendanceStatus.LEAVE
            "CANCELLED" -> AttendanceStatus.CANCELLED
            else -> throw IllegalArgumentException("Invalid status: ${request.status}. Must be 'present', 'absent', 'leave', or 'cancelled'")
        }
        
        // Validate: For future dates, only allow CANCELLED status
        val today = LocalDate.now()
        if (lectureDate.isAfter(today)) {
            if (status != AttendanceStatus.CANCELLED) {
                throw IllegalArgumentException("For future dates, you can only mark lectures as 'cancelled'. Cannot mark as 'present' or 'absent'.")
            }
        }
        
        // Validate subject exists
        val subject = subjectRepositoryAppAction.findById(subjectId)
            ?: throw IllegalArgumentException("Subject not found: ${request.subjectId}")
        
        // Determine if time-specific attendance is being marked
        val hasTimeSlot = request.timeSlot != null
        val hasCustomTimes = request.startTime != null && request.endTime != null
        
        // Validate time information if provided
        if (hasTimeSlot && hasCustomTimes) {
            throw IllegalArgumentException("Cannot provide both timeSlot and custom times")
        }
        
        // Find existing attendance record (time-specific or general)
        val existingAttendance = when {
            hasTimeSlot -> {
                // Find by time slot
                val slotId = (request.timeSlot!! + 1).toShort()
                attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndTimeSlotId(
                    studentId, subjectId, lectureDate, slotId
                )
            }
            hasCustomTimes -> {
                // Find by custom times
                val startTime = try {
                    LocalTime.parse(request.startTime, DateTimeFormatter.ofPattern("HH:mm"))
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid start time format: ${request.startTime}. Use HH:mm format")
                }
                val endTime = try {
                    LocalTime.parse(request.endTime, DateTimeFormatter.ofPattern("HH:mm"))
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid end time format: ${request.endTime}. Use HH:mm format")
                }
                attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndCustomStartTimeAndCustomEndTime(
                    studentId, subjectId, lectureDate, startTime, endTime
                )
            }
            else -> {
                // Backward compatibility: find by date+subject only (no time info)
                attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDate(
                    studentId, subjectId, lectureDate
                )
            }
        }
        
        return if (existingAttendance != null) {
            // Update existing record
            existingAttendance.status = status
            existingAttendance.sourceId = AttendanceSource.STUDENT
            attendanceRepositoryAppAction.save(existingAttendance)
        } else {
            // Create new record
            DMAttendance().apply {
                this.student = student
                this.subject = subject
                this.lectureDate = lectureDate
                this.status = status
                this.sourceId = AttendanceSource.STUDENT
                
                // Set time information if provided
                if (hasTimeSlot) {
                    val slotId = (request.timeSlot!! + 1).toShort()
                    this.timeSlot = timeSlotRepository.findById(slotId).orElse(null)
                    this.customStartTime = null
                    this.customEndTime = null
                } else if (hasCustomTimes) {
                    this.timeSlot = null
                    this.customStartTime = LocalTime.parse(request.startTime, DateTimeFormatter.ofPattern("HH:mm"))
                    this.customEndTime = LocalTime.parse(request.endTime, DateTimeFormatter.ofPattern("HH:mm"))
                } else {
                    // Backward compatibility: no time info
                    this.timeSlot = null
                    this.customStartTime = null
                    this.customEndTime = null
                }
            }.let { attendanceRepositoryAppAction.save(it) }
        }
    }
}
