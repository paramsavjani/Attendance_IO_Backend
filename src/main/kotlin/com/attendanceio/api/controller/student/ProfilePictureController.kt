package com.attendanceio.api.controller.student

import com.attendanceio.api.application.student.actions.UpdateProfilePictureAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.service.ProfilePictureStorageService
import com.attendanceio.api.util.DemoUserUtil
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.concurrent.TimeUnit

@RestController
class ProfilePictureController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val updateProfilePictureAppAction: UpdateProfilePictureAppAction,
    private val storage: ProfilePictureStorageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/api/student/profile-picture", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<Map<String, Any?>> {
        if (oauth2User == null) return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        if (DemoUserUtil.isDemoUser(oauth2User)) return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        if (!storage.enabled) return ResponseEntity.status(503).body(mapOf("error" to "Photo upload is not available right now"))
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        return try {
            val url = updateProfilePictureAppAction.upload(student, file.bytes)
            log.info("POST /api/student/profile-picture -> 200. sid={}, bytes={}", student.sid, file.size)
            ResponseEntity.ok(mapOf("pictureUrl" to url))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).body(mapOf("error" to (e.message ?: "Invalid photo")))
        }
    }

    @DeleteMapping("/api/student/profile-picture")
    fun remove(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Map<String, Any?>> {
        if (oauth2User == null) return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        if (DemoUserUtil.isDemoUser(oauth2User)) return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        return ResponseEntity.ok(mapOf("pictureUrl" to updateProfilePictureAppAction.remove(student)))
    }

    /** Public, like Google's photo URLs: `<img>` tags can't send a token, and names are random UUIDs. */
    @GetMapping("/api/profile-pictures/{name}")
    fun serve(@PathVariable name: String): ResponseEntity<InputStreamResource> {
        val obj = storage.open(name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(obj.headers()["Content-Type"] ?: MediaType.IMAGE_JPEG_VALUE))
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable()) // a new photo gets a new name
            .body(InputStreamResource(obj))
    }
}
