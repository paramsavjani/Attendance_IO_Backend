package com.attendanceio.api.application.attendance.actions

import com.attendanceio.api.model.attendance.AttendanceSource
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.DMAttendance
import com.attendanceio.api.model.attendance.MarkAttendanceRequest
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class MarkAttendanceAppAction(
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction
) {
    @Transactional
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
        
        // Validate subject exists
        val subject = subjectRepositoryAppAction.findById(subjectId)
            ?: throw IllegalArgumentException("Subject not found: ${request.subjectId}")
        
        // Check if attendance record already exists for this date
        val existingAttendance = attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDate(
            studentId,
            subjectId,
            lectureDate
        )
        
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
            }.let { attendanceRepositoryAppAction.save(it) }
        }
    }
}
