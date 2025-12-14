package com.underworldnetwork.api.controller

import com.underworldnetwork.api.repository.student.StudentRepositoryAppAction
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class AuthController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    @GetMapping("/me")
    fun getCurrentUser(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Map<String, Any?>> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        }

        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)

        return if (student != null) {
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
            ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        }
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Map<String, String>> {
        // Spring Security handles logout automatically
        // This endpoint is just for API consistency
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }
}
