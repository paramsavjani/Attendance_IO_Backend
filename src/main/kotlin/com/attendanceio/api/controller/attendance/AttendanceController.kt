package com.attendanceio.api.controller.attendance

import com.attendanceio.api.application.attendance.actions.GetLabTutorialAttendanceAppAction
import com.attendanceio.api.application.attendance.actions.GetMyAttendanceAppAction
import com.attendanceio.api.application.attendance.actions.MarkAttendanceAppAction
import com.attendanceio.api.model.attendance.MarkAttendanceRequest
import com.attendanceio.api.model.attendance.MarkAttendanceResponse
import com.attendanceio.api.model.attendance.MyAttendanceResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.util.DemoUserUtil
import java.time.LocalDate
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

@RestController
@RequestMapping("/api/attendance")
class AttendanceController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val markAttendanceAppAction: MarkAttendanceAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val getMyAttendanceAppAction: GetMyAttendanceAppAction,
    private val getLabTutorialAttendanceAppAction: GetLabTutorialAttendanceAppAction
) {
    @GetMapping
    fun getMyAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false, defaultValue = "total") view: String
    ): ResponseEntity<MyAttendanceResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()

        val studentId = resolveStudentId(oauth2User) ?: return ResponseEntity.status(404).build()

        val targetDate = try {
            date?.let { LocalDate.parse(it) } ?: LocalDate.now()
        } catch (e: Exception) {
            return ResponseEntity.status(400).build()
        }

        return ResponseEntity.ok(getMyAttendanceAppAction.execute(studentId, targetDate, view))
    }

    @PostMapping
    fun markAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: MarkAttendanceRequest
    ): ResponseEntity<MarkAttendanceResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()

        return try {
            val attendance = markAttendanceAppAction.execute(student, request)
            ResponseEntity.ok(
                MarkAttendanceResponse(
                    message = "Attendance marked successfully",
                    attendanceId = attendance.id,
                    subjectId = request.subjectId,
                    lectureDate = request.lectureDate,
                    status = attendance.status.name.lowercase()
                )
            )
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
        if (oauth2User == null) return ResponseEntity.status(401).build()

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        val studentId = student.id ?: return ResponseEntity.status(404).build()

        val attendance = attendanceRepositoryAppAction.findByStudentId(studentId)
            .firstOrNull { it.id == attendanceId }
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Attendance record not found"))

        attendanceRepositoryAppAction.delete(attendance)
        return ResponseEntity.ok(mapOf("message" to "Attendance deleted successfully"))
    }

    @GetMapping("/lab-tutorial")
    fun getLabTutorialAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?
    ): ResponseEntity<MyAttendanceResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        val studentId = resolveStudentId(oauth2User) ?: return ResponseEntity.status(404).build()
        return ResponseEntity.ok(getLabTutorialAttendanceAppAction.execute(studentId))
    }

    private fun resolveStudentId(oauth2User: OAuth2User): Long? {
        if (DemoUserUtil.isDemoUser(oauth2User)) return DemoUserUtil.DEMO_STUDENT_ID
        val email = oauth2User.getAttribute<String>("email") ?: return null
        val student = studentRepositoryAppAction.findByEmail(email) ?: return null
        return student.id
    }
}
