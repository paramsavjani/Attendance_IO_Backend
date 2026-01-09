package com.attendanceio.api.controller.timetable

import com.attendanceio.api.application.timetable.actions.GetStudentTutorialTimetableAppAction
import com.attendanceio.api.application.timetable.actions.SaveStudentTutorialTimetableAppAction
import com.attendanceio.api.model.timetable.SaveTimetableRequest
import com.attendanceio.api.model.timetable.TimetableResponse
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tutorial-timetable")
class TutorialTimetableController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val getStudentTutorialTimetableAppAction: GetStudentTutorialTimetableAppAction,
    private val saveStudentTutorialTimetableAppAction: SaveStudentTutorialTimetableAppAction
) {
    @GetMapping
    fun getTutorialTimetable(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<TimetableResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        
        val timetable = getStudentTutorialTimetableAppAction.execute(studentId)
        return ResponseEntity.ok(timetable)
    }
    
    @PostMapping
    fun saveTutorialTimetable(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: SaveTimetableRequest
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        return try {
            val result = saveStudentTutorialTimetableAppAction.execute(student, request)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }
}
