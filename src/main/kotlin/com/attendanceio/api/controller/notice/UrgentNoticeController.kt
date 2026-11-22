package com.attendanceio.api.controller.notice

import com.attendanceio.api.application.notice.UrgentNoticeService
import com.attendanceio.api.model.notice.CreateNoticeRequest
import com.attendanceio.api.model.notice.ReportNoticeRequest
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.util.DemoUserUtil
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notices")
class UrgentNoticeController(
    private val urgentNoticeService: UrgentNoticeService,
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    private fun resolveCaller(oauth2User: OAuth2User?): DMStudent? {
        val email = oauth2User?.getAttribute<String>("email") ?: return null
        return studentRepositoryAppAction.findByEmail(email)
    }

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))

    /**
     * Create an urgent notice and notify enrolled classmates.
     */
    @PostMapping
    fun createNotice(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: CreateNoticeRequest
    ): ResponseEntity<Any> {
        if (oauth2User == null) return unauthorized()
        if (DemoUserUtil.isDemoUser(oauth2User)) {
            return ResponseEntity.status(403).body(DemoUserUtil.getDemoErrorResponse())
        }
        val student = resolveCaller(oauth2User) ?: return unauthorized()

        return try {
            val response = urgentNoticeService.createNotice(student, request)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(mapOf("error" to (e.message ?: "Not allowed")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to post notice: ${e.message}"))
        }
    }

    /**
     * Active unseen notices for today across the student's enrolled subjects.
     */
    @GetMapping("/active-today")
    fun getActiveToday(@AuthenticationPrincipal oauth2User: OAuth2User?): ResponseEntity<Any> {
        if (oauth2User == null) return unauthorized()
        val student = resolveCaller(oauth2User) ?: return unauthorized()
        val studentId = student.id ?: return unauthorized()

        val notices = urgentNoticeService.getActiveNoticesForStudent(studentId)
        return ResponseEntity.ok(notices)
    }

    /**
     * Mark a notice as seen ("Got it" button).
     */
    @PostMapping("/{noticeId}/seen")
    fun markSeen(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable noticeId: Long
    ): ResponseEntity<Any> {
        if (oauth2User == null) return unauthorized()
        val student = resolveCaller(oauth2User) ?: return unauthorized()

        urgentNoticeService.markNoticeSeen(noticeId, student)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    /**
     * Report an urgent notice for spam or false alarms.
     */
    @PostMapping("/{noticeId}/report")
    fun reportNotice(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable noticeId: Long,
        @RequestBody(required = false) request: ReportNoticeRequest?
    ): ResponseEntity<Any> {
        if (oauth2User == null) return unauthorized()
        val student = resolveCaller(oauth2User) ?: return unauthorized()

        return try {
            val result = urgentNoticeService.reportNotice(noticeId, student, request?.reason)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to report notice: ${e.message}"))
        }
    }

    /**
     * Delete a notice (Creator only).
     */
    @DeleteMapping("/{noticeId}")
    fun deleteNotice(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable noticeId: Long
    ): ResponseEntity<Any> {
        if (oauth2User == null) return unauthorized()
        val student = resolveCaller(oauth2User) ?: return unauthorized()
        val studentId = student.id ?: return unauthorized()

        return try {
            urgentNoticeService.deleteNotice(noticeId, studentId)
            ResponseEntity.ok(mapOf("message" to "Notice deleted successfully"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(mapOf("error" to (e.message ?: "Forbidden")))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to delete notice: ${e.message}"))
        }
    }
}
