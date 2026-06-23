package com.attendanceio.api.controller.student

import com.attendanceio.api.application.student.actions.DetectSubjectConflictsAppAction
import com.attendanceio.api.application.student.actions.GetBaselineAttendanceAppAction
import com.attendanceio.api.application.student.actions.GetEnrolledSubjectsAppAction
import com.attendanceio.api.application.student.actions.SaveEnrolledSubjectsAppAction
import com.attendanceio.api.application.student.actions.UpdateMinimumCriteriaAppAction
import com.attendanceio.api.application.student.actions.UpdateSleepDurationAppAction
import com.attendanceio.api.application.student.actions.UpdateClassroomLocationAppAction
import com.attendanceio.api.application.student.actions.SaveBaselineAttendanceAppAction
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.student.EnrolledSubjectsResponse
import com.attendanceio.api.model.student.SaveEnrolledSubjectsRequest
import com.attendanceio.api.model.student.SaveEnrolledSubjectsResponse
import com.attendanceio.api.model.student.UpdateMinimumCriteriaRequest
import com.attendanceio.api.model.student.UpdateSleepDurationRequest
import com.attendanceio.api.model.student.UpdateClassroomLocationRequest
import com.attendanceio.api.model.student.SleepDurationResponse
import com.attendanceio.api.model.student.SaveBaselineAttendanceRequest
import com.attendanceio.api.model.student.BaselineAttendanceResponse
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.util.DemoUserUtil
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    private val updateMinimumCriteriaAppAction: UpdateMinimumCriteriaAppAction,
    private val detectSubjectConflictsAppAction: DetectSubjectConflictsAppAction,
    private val updateSleepDurationAppAction: UpdateSleepDurationAppAction,
    private val updateClassroomLocationAppAction: UpdateClassroomLocationAppAction,
    private val saveBaselineAttendanceAppAction: SaveBaselineAttendanceAppAction,
    private val getBaselineAttendanceAppAction: GetBaselineAttendanceAppAction
) {
    private fun resolveStudent(oauth2User: OAuth2User): DMStudent? {
        val email = oauth2User.getAttribute<String>("email") ?: return null
        return studentRepositoryAppAction.findByEmail(email)
    }

    private fun resolveStudentAllowDemo(oauth2User: OAuth2User): DMStudent? =
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            studentRepositoryAppAction.findById(DemoUserUtil.DEMO_STUDENT_ID)
        } else {
            resolveStudent(oauth2User)
        }

    private fun failedEnrollmentResponse(message: String) = SaveEnrolledSubjectsResponse(
        success = false,
        message = message,
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

    @PostMapping("/subjects/check-conflicts")
    fun checkSubjectConflicts(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: SaveEnrolledSubjectsRequest
    ): ResponseEntity<Any> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        }
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(404).build()
        return try {
            val subjectIds = request.subjectIds.mapNotNull { it.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid subject ID: $it") }
            val conflicts = detectSubjectConflictsAppAction.execute(student, subjectIds)
            ResponseEntity.ok(mapOf("hasConflicts" to conflicts.isNotEmpty(), "conflicts" to conflicts, "count" to conflicts.size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }

    @GetMapping("/subjects")
    fun getEnrolledSubjects(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<EnrolledSubjectsResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        val student = resolveStudentAllowDemo(oauth2User) ?: return ResponseEntity.status(404).build()
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        return ResponseEntity.ok(EnrolledSubjectsResponse(subjects = getEnrolledSubjectsAppAction.execute(studentId)))
    }

    @PostMapping("/subjects")
    fun saveEnrolledSubjects(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: SaveEnrolledSubjectsRequest
    ): ResponseEntity<Any> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        }
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(404).build()
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
            if (syncResult.hasConflicts) ResponseEntity.status(209).body(response) else ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(failedEnrollmentResponse(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(failedEnrollmentResponse("Internal server error"))
        }
    }

    @PutMapping("/minimum-criteria")
    fun updateMinimumCriteria(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: UpdateMinimumCriteriaRequest
    ): ResponseEntity<Any> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        }
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(404).build()
        return try {
            updateMinimumCriteriaAppAction.execute(student, request)
            ResponseEntity.ok(mapOf("message" to "Minimum criteria updated successfully"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }

    @GetMapping("/sleep-duration")
    fun getSleepDuration(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<SleepDurationResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        val student = resolveStudentAllowDemo(oauth2User) ?: return ResponseEntity.status(404).build()
        return ResponseEntity.ok(SleepDurationResponse(sleepDurationHours = student.sleepDurationHours))
    }

    @PutMapping("/sleep-duration")
    fun updateSleepDuration(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: UpdateSleepDurationRequest
    ): ResponseEntity<Any> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        }
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(404).build()
        return try {
            updateSleepDurationAppAction.execute(student, request)
            ResponseEntity.ok(mapOf("message" to "Sleep duration updated successfully", "sleepDurationHours" to request.sleepDurationHours))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }

    @PutMapping("/classroom-location")
    fun updateClassroomLocation(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: UpdateClassroomLocationRequest
    ): ResponseEntity<Any> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        }
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(404).build()
        return try {
            updateClassroomLocationAppAction.execute(student, request)
            ResponseEntity.ok(mapOf(
                "message" to "Classroom location updated successfully",
                "subjectId" to request.subjectId,
                "classroomLocation" to (request.classroomLocation ?: "reset to default")
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Internal server error"))
        }
    }

    @GetMapping("/baseline-attendance/{subjectId}")
    fun getBaselineAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable subjectId: String
    ): ResponseEntity<BaselineAttendanceResponse> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        val student = resolveStudentAllowDemo(oauth2User) ?: return ResponseEntity.status(404).build()
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        val subjectIdLong = subjectId.toLongOrNull() ?: return ResponseEntity.status(400).build()
        return ResponseEntity.ok(getBaselineAttendanceAppAction.execute(studentId, subjectIdLong))
    }

    @PostMapping("/baseline-attendance")
    fun saveBaselineAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: SaveBaselineAttendanceRequest
    ): ResponseEntity<Any> {
        if (oauth2User == null) return ResponseEntity.status(401).build()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        }
        val student = resolveStudent(oauth2User) ?: return ResponseEntity.status(404).build()
        return try {
            val saved = saveBaselineAttendanceAppAction.execute(student, request)
            ResponseEntity.ok(mapOf<String, Any?>(
                "message" to "Baseline attendance saved successfully",
                "subjectId" to request.subjectId,
                "cutoffDate" to saved.cutoffDate?.toString(),
                "totalClasses" to saved.totalClasses,
                "presentClasses" to saved.presentClasses
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf<String, Any?>("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf<String, Any?>("error" to "Internal server error"))
        }
    }
}
