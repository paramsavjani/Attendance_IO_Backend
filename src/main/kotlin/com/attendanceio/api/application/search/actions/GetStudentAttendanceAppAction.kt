package com.attendanceio.api.application.search.actions

import com.attendanceio.api.application.search.adapters.StudentAttendanceAdapter
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.search.StudentAttendanceResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class GetStudentAttendanceAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
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
        
        // Calculate computed total classes including today for each subject
        val today = LocalDate.now()
        val computedTotals = attendanceResults.associate { result ->
            // Get timetable entries for this subject
            val subjectTimetableEntries = allTimetableEntries.filter { 
                it.subject?.id == result.subjectId 
            }
            
            // Calculate total expected classes based on timetable (including today)
            val computedTotalClasses = classCalculationService.calculateTotalClasses(
                subjectTimetableEntries,
                today
            )
            
            // Count cancelled classes for this subject up to today
            val cancelledCount = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(today) &&
                    it.status == AttendanceStatus.CANCELLED
                }
                .size
            
            // Use computed total if available, otherwise fall back to attendance-based total
            // Subtract cancelled classes from total
            val totalClasses = if (computedTotalClasses > 0) {
                computedTotalClasses - cancelledCount
            } else {
                (result.baseTotal + result.totalAfterCutoff) - cancelledCount
            }
            
            result.subjectId to maxOf(0, totalClasses) // Ensure total is not negative
        }
        
        // Use adapter to convert to response model
        return studentAttendanceAdapter.toResponse(
            studentId = studentId,
            studentName = student.name ?: "",
            rollNumber = student.sid,
            studentPictureUrl = student.pictureUrl,
            attendanceResults = attendanceResults,
            computedTotals = computedTotals
        )
    }
}
