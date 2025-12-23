package com.attendanceio.api.controller.attendance

import com.attendanceio.api.application.attendance.actions.MarkAttendanceAppAction
import com.attendanceio.api.model.attendance.AttendanceCalculationResult
import com.attendanceio.api.model.attendance.MarkAttendanceRequest
import com.attendanceio.api.model.attendance.MarkAttendanceResponse
import com.attendanceio.api.model.attendance.MyAttendanceResponse
import com.attendanceio.api.model.attendance.SubjectStatsResponse
import com.attendanceio.api.model.attendance.TodayAttendanceRecord
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.service.AttendanceCalculationService
import com.attendanceio.api.service.ClassCalculationService
import org.springframework.cache.annotation.CacheEvict
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/attendance")
class AttendanceController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val markAttendanceAppAction: MarkAttendanceAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val classCalculationService: ClassCalculationService,
    private val attendanceCalculationService: AttendanceCalculationService
) {
    @GetMapping
    fun getMyAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestParam(required = false) date: String?
    ): ResponseEntity<MyAttendanceResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        
        // Parse date parameter or use today
        val targetDate = try {
            date?.let { LocalDate.parse(it) } ?: LocalDate.now()
        } catch (e: Exception) {
            return ResponseEntity.status(400).build()
        }
        
        // Get attendance statistics for all subjects
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        
        // Get student's timetable for current semester
        val currentSemester = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()
        val studentTimetable = if (currentSemester != null) {
            studentTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)
        } else {
            emptyList()
        }
        
        // Get all attendance records to count cancelled classes
        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)
        
        // Convert to subject stats with computed total classes
        val subjectStats = attendanceResults.map { result ->
            val totalPresent = result.basePresent + result.presentAfterCutoff
            val totalAbsent = result.baseAbsent + result.absentAfterCutoff
            
            // Get timetable entries for this subject
            val subjectTimetableEntries = studentTimetable.filter { 
                it.subject?.id == result.subjectId 
            }
            
            // Calculate total expected classes based on timetable up to target date
            val computedTotalClasses = classCalculationService.calculateTotalClasses(
                subjectTimetableEntries,
                targetDate
            )
            
            // Calculate total expected classes until end date (April 30)
            val endDate = classCalculationService.getConfiguredEndDate() ?: targetDate
            val computedTotalUntilEndDate = classCalculationService.calculateTotalClasses(
                subjectTimetableEntries,
                endDate
            )
            
            // Count cancelled classes for this subject up to target date
            val cancelledCount = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate) &&
                    it.status == com.attendanceio.api.model.attendance.AttendanceStatus.CANCELLED
                }
                .size
            
            // Count cancelled classes until end date
            val cancelledUntilEndDate = allAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(endDate) &&
                    it.status == com.attendanceio.api.model.attendance.AttendanceStatus.CANCELLED
                }
                .size
            
            // Use computed total if available, otherwise fall back to attendance-based total
            // Subtract cancelled classes from total
            val totalClasses = if (computedTotalClasses > 0) {
                computedTotalClasses - cancelledCount
            } else {
                (result.baseTotal + result.totalAfterCutoff) - cancelledCount
            }
            
            // Total classes until end date (for bunkable calculation)
            val totalUntilEndDate = if (computedTotalUntilEndDate > 0) {
                computedTotalUntilEndDate - cancelledUntilEndDate
            } else {
                // Fallback: estimate based on current total and remaining time
                totalClasses
            }
            
            val finalTotal = maxOf(0, totalClasses) // Ensure total is not negative
            val finalTotalUntilEndDate = maxOf(finalTotal, maxOf(0, totalUntilEndDate)) // At least current total
            
            // Get minimum criteria for this subject (default to 75 if not set)
            val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, result.subjectId)
            val minRequired = studentSubject?.minimumCriteria ?: 75
            
            // Calculate attendance metrics
            val percentage = attendanceCalculationService.calculatePercentage(totalPresent, finalTotal)
            val classesNeeded = attendanceCalculationService.calculateClassesNeeded(totalPresent, finalTotal, minRequired)
            // Calculate bunkable classes based on total until end date
            val bunkableClasses = attendanceCalculationService.calculateBunkableClasses(
                totalPresent, 
                finalTotal, 
                finalTotalUntilEndDate, 
                minRequired
            )
            
            SubjectStatsResponse(
                subjectId = result.subjectId.toString(),
                present = totalPresent,
                absent = totalAbsent,
                total = finalTotal,
                totalUntilEndDate = finalTotalUntilEndDate,
                percentage = percentage,
                classesNeeded = classesNeeded,
                bunkableClasses = bunkableClasses
            )
        }
        
        // Get attendance records for the specified date
        val dateAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)
            .filter { it.lectureDate == targetDate }
            .map { attendance ->
                TodayAttendanceRecord(
                    attendanceId = attendance.id,
                    subjectId = attendance.subject?.id?.toString() ?: "",
                    lectureDate = attendance.lectureDate?.toString() ?: "",
                    status = attendance.status.name.lowercase()
                )
            }
        
        val response = MyAttendanceResponse(
            subjectStats = subjectStats,
            todayAttendance = dateAttendanceRecords
        )
        
        return ResponseEntity.ok(response)
    }
    @PostMapping
    fun markAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: MarkAttendanceRequest
    ): ResponseEntity<MarkAttendanceResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        return try {
            val attendance = markAttendanceAppAction.execute(student, request)
            val response = MarkAttendanceResponse(
                message = "Attendance marked successfully",
                attendanceId = attendance.id,
                subjectId = request.subjectId,
                lectureDate = request.lectureDate,
                status = attendance.status.name.lowercase()
            )
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).build()
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }
    
    @DeleteMapping("/{attendanceId}")
    @CacheEvict(value = ["analytics"], allEntries = true)
    fun deleteAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable attendanceId: Long
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        
        // Find attendance record and verify it belongs to the student
        val attendance = attendanceRepositoryAppAction.findByStudentId(studentId)
            .firstOrNull { it.id == attendanceId }
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Attendance record not found"))
        
        // Delete the attendance record
        attendanceRepositoryAppAction.delete(attendance)
        
        return ResponseEntity.ok(mapOf("message" to "Attendance deleted successfully"))
    }
}

