package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.student.SaveBaselineAttendanceRequest
import com.attendanceio.api.model.attendance.DMInstituteAttendance
import com.attendanceio.api.repository.attendance.InstituteAttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class SaveBaselineAttendanceAppAction(
    private val instituteAttendanceRepositoryAppAction: InstituteAttendanceRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction
) {
    @Transactional
    fun execute(student: DMStudent, request: SaveBaselineAttendanceRequest): DMInstituteAttendance {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        
        val subjectId = request.subjectId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid subject ID: ${request.subjectId}")
        
        // Validate that the subject is enrolled
        val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            ?: throw IllegalArgumentException("Subject not found in enrolled subjects")
        
        // Validate subject exists
        val subject = subjectRepositoryAppAction.findById(subjectId)
            ?: throw IllegalArgumentException("Subject not found")
        
        // Validate dates
        val cutoffDate = try {
            LocalDate.parse(request.cutoffDate, DateTimeFormatter.ISO_DATE)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date format. Use yyyy-MM-dd format")
        }
        
        // Validate numbers
        if (request.totalClasses < 0) {
            throw IllegalArgumentException("Total classes cannot be negative")
        }
        if (request.presentClasses < 0) {
            throw IllegalArgumentException("Present classes cannot be negative")
        }
        if (request.presentClasses > request.totalClasses) {
            throw IllegalArgumentException("Present classes cannot exceed total classes")
        }
        
        // Delete existing baseline attendance for this subject (if any)
        instituteAttendanceRepositoryAppAction.deleteAllByStudentIdAndSubjectId(studentId, subjectId)
        
        // Create new baseline attendance record
        val baselineAttendance = DMInstituteAttendance().apply {
            this.student = student
            this.subject = subject
            this.cutoffDate = cutoffDate
            this.totalClasses = request.totalClasses
            this.presentClasses = request.presentClasses
        }
        
        return instituteAttendanceRepositoryAppAction.save(baselineAttendance)
    }
}

