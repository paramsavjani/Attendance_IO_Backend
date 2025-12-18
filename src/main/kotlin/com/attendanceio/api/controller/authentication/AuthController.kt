package com.attendanceio.api.controller

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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class AuthController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        if (oauth2User == null) {
            log.debug("GET /api/user/me -> 401 (no principal). sessionId={}", request.getSession(false)?.id)
            return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)

        return if (student != null) {
            log.debug("GET /api/user/me -> 200. email={}, sessionId={}", email, request.getSession(false)?.id)
            ResponseEntity.ok(
                mapOf(
                    "id" to student.id,
                    "email" to student.email,
                    "name" to student.name,
                    "pictureUrl" to student.pictureUrl,
                    "sid" to student.sid,
                    "phone" to student.phone
                )
            )
        } else {
            log.debug("GET /api/user/me -> 404 (student not found). email={}, sessionId={}", email, request.getSession(false)?.id)
            ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        }
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        // Invalidate session and clear security context
        SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().authentication)
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }
}
