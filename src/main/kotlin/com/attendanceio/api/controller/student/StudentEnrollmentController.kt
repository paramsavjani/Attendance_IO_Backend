package com.attendanceio.api.controller.student

import com.attendanceio.api.application.student.actions.GetEnrolledSubjectsAppAction
import com.attendanceio.api.application.student.actions.SaveEnrolledSubjectsAppAction
import com.attendanceio.api.application.student.actions.UpdateMinimumCriteriaAppAction
import com.attendanceio.api.model.student.EnrolledSubjectsResponse
import com.attendanceio.api.model.student.SaveEnrolledSubjectsRequest
import com.attendanceio.api.model.student.UpdateMinimumCriteriaRequest
import com.attendanceio.api.model.timetable.SubjectInfo
import com.attendanceio.api.model.timetable.TimetableConflict
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
    ): ResponseEntity<SaveEnrolledSubjectsResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        
        return try {
            val result = saveEnrolledSubjectsAppAction.execute(student, request)
            val syncResult = result.syncResult
            
            val response = SaveEnrolledSubjectsResponse(
                success = syncResult.success,
                message = syncResult.message,
                subjectIds = result.subjectIds,
                count = result.subjectIds.size,
                hasConflicts = syncResult.hasConflicts,
                conflicts = syncResult.conflicts,
                addedSubjects = syncResult.addedSubjects,
                removedSubjects = syncResult.removedSubjects,
                subjectsWithConflicts = syncResult.subjectsWithConflicts,
                timetableSlotsAdded = syncResult.timetableSlotsAdded,
                timetableSlotsRemoved = syncResult.timetableSlotsRemoved
            )
            
            // Return 200 OK even if there are conflicts (subjects were enrolled, conflicts need resolution)
            // Use 409 Conflict status to indicate conflicts need user attention
            if (syncResult.hasConflicts) {
                ResponseEntity.status(209).body(response) // 209 = partial success with conflicts
            } else {
                ResponseEntity.ok(response)
            }
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(
                SaveEnrolledSubjectsResponse(
                    success = false,
                    message = e.message ?: "Invalid request",
                    subjectIds = emptyList(),
                    count = 0,
                    hasConflicts = false,
                    conflicts = emptyList(),
                    addedSubjects = emptyList(),
                    removedSubjects = emptyList(),
                    subjectsWithConflicts = emptyList(),
                    timetableSlotsAdded = 0,
                    timetableSlotsRemoved = 0
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                SaveEnrolledSubjectsResponse(
                    success = false,
                    message = "Internal server error",
                    subjectIds = emptyList(),
                    count = 0,
                    hasConflicts = false,
                    conflicts = emptyList(),
                    addedSubjects = emptyList(),
                    removedSubjects = emptyList(),
                    subjectsWithConflicts = emptyList(),
                    timetableSlotsAdded = 0,
                    timetableSlotsRemoved = 0
                )
            )
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

/**
 * Response for save enrolled subjects endpoint.
 * Includes timetable sync details and conflict information.
 */
data class SaveEnrolledSubjectsResponse(
    val success: Boolean,
    val message: String,
    val subjectIds: List<String>,
    val count: Int,
    val hasConflicts: Boolean,
    val conflicts: List<TimetableConflict>,
    val addedSubjects: List<SubjectInfo>,
    val removedSubjects: List<SubjectInfo>,
    val subjectsWithConflicts: List<SubjectInfo>,
    val timetableSlotsAdded: Int,
    val timetableSlotsRemoved: Int
)

