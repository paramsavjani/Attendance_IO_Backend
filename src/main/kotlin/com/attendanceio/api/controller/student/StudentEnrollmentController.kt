package com.attendanceio.api.controller.student

import com.attendanceio.api.application.student.actions.GetEnrolledSubjectsAppAction
import com.attendanceio.api.application.student.actions.SaveEnrolledSubjectsAppAction
import com.attendanceio.api.application.student.actions.UpdateMinimumCriteriaAppAction
import com.attendanceio.api.model.student.EnrolledSubjectsResponse
import com.attendanceio.api.model.student.SaveEnrolledSubjectsRequest
import com.attendanceio.api.model.student.UpdateMinimumCriteriaRequest
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/student/enrollment")
class StudentEnrollmentController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val getEnrolledSubjectsAppAction: GetEnrolledSubjectsAppAction,
    private val saveEnrolledSubjectsAppAction: SaveEnrolledSubjectsAppAction,
    private val updateMinimumCriteriaAppAction: UpdateMinimumCriteriaAppAction
) {
    @GetMapping("/subjects")
    fun getEnrolledSubjects(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<EnrolledSubjectsResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        
        val enrolledSubjects = getEnrolledSubjectsAppAction.execute(studentId)
        
        return ResponseEntity.ok(EnrolledSubjectsResponse(subjects = enrolledSubjects))
    }
    
    @PostMapping("/subjects")
    fun saveEnrolledSubjects(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: SaveEnrolledSubjectsRequest
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        
        return try {
            val savedSubjectIds = saveEnrolledSubjectsAppAction.execute(student, request)
            ResponseEntity.ok(mapOf(
                "message" to "Subjects enrolled successfully",
                "subjectIds" to savedSubjectIds,
                "count" to savedSubjectIds.size
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }
    
    @PutMapping("/minimum-criteria")
    fun updateMinimumCriteria(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: UpdateMinimumCriteriaRequest
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        return try {
            updateMinimumCriteriaAppAction.execute(student, request)
            ResponseEntity.ok(mapOf(
                "message" to "Minimum criteria updated successfully"
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }
}

