package com.attendanceio.api.controller.timetable

import com.attendanceio.api.application.timetable.actions.GetStudentLabTimetableAppAction
import com.attendanceio.api.application.timetable.actions.SaveStudentLabTimetableAppAction
import com.attendanceio.api.model.timetable.SaveTimetableRequest
import com.attendanceio.api.model.timetable.TimetableResponse
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.util.DemoUserUtil
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/lab-timetable")
class LabTimetableController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val getStudentLabTimetableAppAction: GetStudentLabTimetableAppAction,
    private val saveStudentLabTimetableAppAction: SaveStudentLabTimetableAppAction
) {
    private fun resolveStudentId(oauth2User: OAuth2User): Long? {
        if (DemoUserUtil.isDemoUser(oauth2User)) return DemoUserUtil.DEMO_STUDENT_ID
        val email = oauth2User.getAttribute<String>("email") ?: return null
        return studentRepositoryAppAction.findByEmail(email)?.id
    }

    @GetMapping
    fun getLabTimetable(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<TimetableResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        val studentId = resolveStudentId(oauth2User) ?: return ResponseEntity.status(404).build()
        return ResponseEntity.ok(getStudentLabTimetableAppAction.execute(studentId))
    }

    @PostMapping
    fun saveLabTimetable(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: SaveTimetableRequest
    ): ResponseEntity<Any> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        }
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        return try {
            ResponseEntity.ok(saveStudentLabTimetableAppAction.execute(student, request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }
}
