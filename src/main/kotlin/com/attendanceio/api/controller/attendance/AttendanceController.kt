package com.attendanceio.api.controller.attendance

import com.attendanceio.api.application.attendance.actions.MarkAttendanceAppAction
import com.attendanceio.api.model.attendance.MarkAttendanceRequest
import com.attendanceio.api.model.attendance.MarkAttendanceResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/attendance")
class AttendanceController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val markAttendanceAppAction: MarkAttendanceAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction
) {
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

