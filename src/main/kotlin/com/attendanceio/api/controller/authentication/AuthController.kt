package com.attendanceio.api.controller.authentication

import com.attendanceio.api.application.student.actions.GetEnrolledSubjectsAppAction
import com.attendanceio.api.application.student.actions.UpdateFcmTokenAppAction
import com.attendanceio.api.application.student.actions.UpdateNotificationPreferencesAppAction
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.util.DemoUserUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class AuthController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val getEnrolledSubjectsAppAction: GetEnrolledSubjectsAppAction,
    private val updateFcmTokenAppAction: UpdateFcmTokenAppAction,
    private val updateNotificationPreferencesAppAction: UpdateNotificationPreferencesAppAction
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @GetMapping("/me")
    fun getCurrentUser(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Map<String, Any?>> {
        if (oauth2User == null) return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))

        if (DemoUserUtil.isDemoUser(oauth2User)) {
            val email = oauth2User.getAttribute<String>("email") ?: "demo@example.com"
            val demoStudent = studentRepositoryAppAction.findById(DemoUserUtil.DEMO_STUDENT_ID)
            return if (demoStudent != null) {
                log.debug("GET /api/user/me -> 200 (demo). email={}", email)
                ResponseEntity.ok(demoStudent.toUserMap(email, isDemo = true))
            } else {
                log.warn("GET /api/user/me -> demo student not found. email={}", email)
                ResponseEntity.ok(demoFallbackMap(oauth2User, email))
            }
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
        return if (student != null) {
            log.debug("GET /api/user/me -> 200. email={}", email)
            ResponseEntity.ok(student.toUserMap(student.email, isDemo = false))
        } else {
            log.debug("GET /api/user/me -> 404. email={}", email)
            ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        }
    }

    @GetMapping("/init")
    fun getCurrentUserWithSubjects(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Map<String, Any?>> {
        if (oauth2User == null) return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))

        if (DemoUserUtil.isDemoUser(oauth2User)) {
            val email = oauth2User.getAttribute<String>("email") ?: "demo@example.com"
            val demoStudent = studentRepositoryAppAction.findById(DemoUserUtil.DEMO_STUDENT_ID)
            return if (demoStudent != null && demoStudent.id != null) {
                val subjects = fetchSubjectMaps(demoStudent.id!!, "demo student ${demoStudent.id}")
                log.debug("GET /api/user/init -> 200 (demo). email={}, subjectsCount={}", email, subjects.size)
                ResponseEntity.ok(demoStudent.toUserMap(email, isDemo = true) + mapOf("subjects" to subjects))
            } else {
                log.warn("GET /api/user/init -> demo student not found. email={}", email)
                ResponseEntity.ok(demoFallbackMap(oauth2User, email) + mapOf("subjects" to emptyList<Map<String, Any>>()))
            }
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
        return if (student != null) {
            val studentId = student.id
                ?: return ResponseEntity.status(404).body(mapOf("error" to "Student ID not found"))
            val subjects = fetchSubjectMaps(studentId, "student $studentId")
            log.debug("GET /api/user/init -> 200. email={}, subjectsCount={}", email, subjects.size)
            ResponseEntity.ok(student.toUserMap(student.email, isDemo = false) + mapOf("subjects" to subjects))
        } else {
            log.debug("GET /api/user/init -> 404. email={}", email)
            ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        }
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        // Invalidate session if it exists (backward compatibility with old clients)
        val session = request.getSession(false)
        if (session != null) {
            SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().authentication)
        }
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }

    data class UpdateFcmTokenRequest(val fcmToken: String?)
    data class UpdateNotificationPreferencesRequest(val dailyReminderHours: List<Int>?, val afterLectureReminderEnabled: Boolean?)

    @PutMapping("/notification-preferences")
    fun updateNotificationPreferences(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: UpdateNotificationPreferencesRequest
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        if (DemoUserUtil.isDemoUser(oauth2User)) return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        val updated = updateNotificationPreferencesAppAction.execute(
            student,
            UpdateNotificationPreferencesAppAction.Request(request.dailyReminderHours, request.afterLectureReminderEnabled)
        )
        log.info("PUT /api/user/notification-preferences -> 200. email={}, dailyReminderHours={}, afterLectureReminderEnabled={}", email, updated.reminderHours(), updated.afterLectureReminderEnabled)
        return ResponseEntity.ok(mapOf(
            "message" to "Notification preferences updated",
            "dailyReminderHours" to updated.reminderHours(),
            "afterLectureReminderEnabled" to updated.afterLectureReminderEnabled
        ))
    }

    @PutMapping("/fcm-token")
    fun updateFcmToken(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: UpdateFcmTokenRequest
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        if (DemoUserUtil.isDemoUser(oauth2User)) return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        updateFcmTokenAppAction.execute(student.id!!, request.fcmToken)
        log.info("PUT /api/user/fcm-token -> 200. email={}, tokenUpdated={}", email, request.fcmToken != null)
        return ResponseEntity.ok(mapOf("message" to "FCM token updated successfully", "fcmToken" to (request.fcmToken ?: "")))
    }

    private fun DMStudent.toUserMap(emailOverride: String?, isDemo: Boolean): Map<String, Any?> = mapOf(
        "id" to id,
        "email" to emailOverride,
        "name" to name,
        "pictureUrl" to pictureUrl,
        "sid" to sid,
        "phone" to phone,
        "fcmToken" to (fcmToken ?: ""),
        "dailyReminderHours" to reminderHours(),
        "afterLectureReminderEnabled" to afterLectureReminderEnabled,
        "isDemo" to isDemo
    )

    private fun DMStudent.reminderHours(): List<Int> = buildList {
        if (dailyReminderAt18) add(18)
        if (dailyReminderAt20) add(20)
        if (dailyReminderAt22) add(22)
    }

    private fun demoFallbackMap(oauth2User: OAuth2User, email: String): Map<String, Any?> = mapOf(
        "id" to DemoUserUtil.DEMO_STUDENT_ID,
        "email" to email,
        "name" to "Demo User",
        "pictureUrl" to oauth2User.getAttribute<String>("picture"),
        "sid" to "DEMO",
        "phone" to "",
        "fcmToken" to "",
        "dailyReminderHours" to emptyList<Int>(),
        "afterLectureReminderEnabled" to true,
        "isDemo" to true
    )

    private fun fetchSubjectMaps(studentId: Long, logLabel: String): List<Map<String, Any?>> = try {
        getEnrolledSubjectsAppAction.execute(studentId).map { s ->
            mapOf<String, Any?>(
                "subjectId" to s.subjectId,
                "subjectCode" to s.subjectCode,
                "subjectName" to s.subjectName,
                "lecturePlace" to s.lecturePlace,
                "classroomLocation" to s.classroomLocation,
                "color" to s.color,
                "minimumCriteria" to s.minimumCriteria
            )
        }
    } catch (e: Exception) {
        log.warn("Failed to fetch enrolled subjects for {}: {}", logLabel, e.message)
        emptyList()
    }
}
