package com.attendanceio.api.controller

import com.attendanceio.api.application.student.actions.GetEnrolledSubjectsAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
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
    private val getEnrolledSubjectsAppAction: GetEnrolledSubjectsAppAction
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal oauth2User: OAuth2User?
    ): ResponseEntity<Map<String, Any?>> {
        if (oauth2User == null) {
            log.debug("GET /api/user/me -> 401 (no principal)")
            return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        }

        // Check if user is in demo mode - show student ID 790's data
        if (com.attendanceio.api.util.DemoUserUtil.isDemoUser(oauth2User)) {
            val demoStudentId = com.attendanceio.api.util.DemoUserUtil.DEMO_STUDENT_ID
            val demoStudent = studentRepositoryAppAction.findById(demoStudentId)
            val email = oauth2User.getAttribute<String>("email") ?: "demo@example.com"
            
            return if (demoStudent != null) {
                log.debug("GET /api/user/me -> 200 (demo user showing student {}). email={}", demoStudentId, email)
                ResponseEntity.ok(
                    mapOf(
                        "id" to demoStudent.id,
                        "email" to email, // Keep demo user's email
                        "name" to demoStudent.name,
                        "pictureUrl" to demoStudent.pictureUrl,
                        "sid" to demoStudent.sid,
                        "phone" to demoStudent.phone,
                        "fcmToken" to (demoStudent.fcmToken ?: ""),
                        "isDemo" to true
                    )
                )
            } else {
                // Demo student not found - return default demo data so user can still log in
                log.warn("GET /api/user/me -> 200 (demo student {} not found, returning default data). email={}", demoStudentId, email)
                ResponseEntity.ok(
                    mapOf(
                        "id" to demoStudentId,
                        "email" to email,
                        "name" to "Demo User",
                        "pictureUrl" to oauth2User.getAttribute<String>("picture"),
                        "sid" to "DEMO",
                        "phone" to "",
                        "fcmToken" to "",
                        "isDemo" to true
                    )
                )
            }
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)

        return if (student != null) {
            log.debug("GET /api/user/me -> 200. email={}", email)
            ResponseEntity.ok(
                mapOf(
                    "id" to student.id,
                    "email" to student.email,
                    "name" to student.name,
                    "pictureUrl" to student.pictureUrl,
                    "sid" to student.sid,
                    "phone" to student.phone,
                    "fcmToken" to (student.fcmToken ?: ""),
                    "isDemo" to false
                )
            )
        } else {
            log.debug("GET /api/user/me -> 404 (student not found). email={}", email)
            ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        }
    }

    @GetMapping("/init")
    fun getCurrentUserWithSubjects(
        @AuthenticationPrincipal oauth2User: OAuth2User?
    ): ResponseEntity<Map<String, Any?>> {
        if (oauth2User == null) {
            log.debug("GET /api/user/init -> 401 (no principal)")
            return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        }

        // Check if user is in demo mode - show student ID 790's data
        if (com.attendanceio.api.util.DemoUserUtil.isDemoUser(oauth2User)) {
            val demoStudentId = com.attendanceio.api.util.DemoUserUtil.DEMO_STUDENT_ID
            val demoStudent = studentRepositoryAppAction.findById(demoStudentId)
            val email = oauth2User.getAttribute<String>("email") ?: "demo@example.com"
            
            return if (demoStudent != null && demoStudent.id != null) {
                // Get enrolled subjects for demo student
                val enrolledSubjects = try {
                    getEnrolledSubjectsAppAction.execute(demoStudent.id!!)
                } catch (e: Exception) {
                    log.warn("Failed to fetch enrolled subjects for demo student {}: {}", demoStudentId, e.message)
                    emptyList()
                }

                log.debug("GET /api/user/init -> 200 (demo user showing student {}). email={}, subjectsCount={}", demoStudentId, email, enrolledSubjects.size)
                ResponseEntity.ok(
                    mapOf(
                        "id" to demoStudent.id,
                        "email" to email, // Keep demo user's email
                        "name" to demoStudent.name,
                        "pictureUrl" to demoStudent.pictureUrl,
                        "sid" to demoStudent.sid,
                        "phone" to demoStudent.phone,
                        "fcmToken" to (demoStudent.fcmToken ?: ""),
                        "isDemo" to true,
                        "subjects" to enrolledSubjects.map { subject ->
                            mapOf(
                                "subjectId" to subject.subjectId,
                                "subjectCode" to subject.subjectCode,
                                "subjectName" to subject.subjectName,
                                "lecturePlace" to subject.lecturePlace,
                                "classroomLocation" to subject.classroomLocation,
                                "color" to subject.color,
                                "minimumCriteria" to subject.minimumCriteria
                            )
                        }
                    )
                )
            } else {
                // Demo student not found - return default demo data so user can still log in
                log.warn("GET /api/user/init -> 200 (demo student {} not found, returning default data). email={}", demoStudentId, email)
                ResponseEntity.ok(
                    mapOf(
                        "id" to demoStudentId,
                        "email" to email,
                        "name" to "Demo User",
                        "pictureUrl" to oauth2User.getAttribute<String>("picture"),
                        "sid" to "DEMO",
                        "phone" to "",
                        "fcmToken" to "",
                        "isDemo" to true,
                        "subjects" to emptyList<Map<String, Any>>()
                    )
                )
            }
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)

        return if (student != null) {
            val studentId = student.id
            if (studentId == null) {
                log.debug("GET /api/user/init -> 404 (student ID is null). email={}", email)
                return ResponseEntity.status(404).body(mapOf("error" to "Student ID not found"))
            }

            // Get enrolled subjects
            val enrolledSubjects = try {
                getEnrolledSubjectsAppAction.execute(studentId)
            } catch (e: Exception) {
                log.warn("Failed to fetch enrolled subjects for student {}: {}", studentId, e.message)
                emptyList()
            }

            log.debug("GET /api/user/init -> 200. email={}, subjectsCount={}", email, enrolledSubjects.size)
            ResponseEntity.ok(
                mapOf(
                    "id" to student.id,
                    "email" to student.email,
                    "name" to student.name,
                    "pictureUrl" to student.pictureUrl,
                    "sid" to student.sid,
                    "phone" to student.phone,
                    "fcmToken" to (student.fcmToken ?: ""),
                    "isDemo" to false,
                    "subjects" to enrolledSubjects.map { subject ->
                        mapOf(
                            "subjectId" to subject.subjectId,
                            "subjectCode" to subject.subjectCode,
                            "subjectName" to subject.subjectName,
                            "lecturePlace" to subject.lecturePlace,
                            "classroomLocation" to subject.classroomLocation,
                            "color" to subject.color,
                            "minimumCriteria" to subject.minimumCriteria
                        )
                    }
                )
            )
        } else {
            log.debug("GET /api/user/init -> 404 (student not found). email={}", email)
            ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        }
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        // Invalidate session if it exists (for backward compatibility with old clients)
        val session = request.getSession(false)
        if (session != null) {
            SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().authentication)
            log.debug("Logout: Session invalidated for backward compatibility")
        }
        // For JWT clients, logout is handled client-side by removing the token
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }

    data class UpdateFcmTokenRequest(val fcmToken: String?)

    @PutMapping("/fcm-token")
    fun updateFcmToken(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: UpdateFcmTokenRequest
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) {
            log.debug("PUT /api/user/fcm-token -> 401 (no principal)")
            return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        }

        // Block all updates for demo users
        if (com.attendanceio.api.util.DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(com.attendanceio.api.util.DemoUserUtil.getDemoErrorResponse())
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)

        return if (student != null) {
            student.fcmToken = request.fcmToken
            studentRepositoryAppAction.update(student)
            log.info("PUT /api/user/fcm-token -> 200. email={}, tokenUpdated={}", email, request.fcmToken != null)
            ResponseEntity.ok(mapOf("message" to "FCM token updated successfully", "fcmToken" to (request.fcmToken ?: "")))
        } else {
            log.debug("PUT /api/user/fcm-token -> 404 (student not found). email={}", email)
            ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        }
    }
}
