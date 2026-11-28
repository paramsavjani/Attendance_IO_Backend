package com.attendanceio.api.controller.placement

import com.attendanceio.api.application.placement.ExperienceSubmissionAppAction
import com.attendanceio.api.application.placement.ExperienceSubmissionRequest
import com.attendanceio.api.application.placement.PlacementAccess
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import com.attendanceio.api.application.placement.PlacementExperienceQueryAppAction
import com.attendanceio.api.application.placement.PlacementQueryAppAction
import com.attendanceio.api.service.PlacementJdStorageService
import com.attendanceio.api.util.DemoUserUtil
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import java.util.concurrent.TimeUnit
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.server.ResponseStatusException
import com.attendanceio.api.model.college.DMPlacementCompany
import com.attendanceio.api.model.college.DMPlacementExperience
import com.attendanceio.api.model.placement.ExperienceListResponse
import com.attendanceio.api.model.placement.PlacementListResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** This season's placement drives and results. DAU students only (see [PlacementAccess]): it names selected students. */
@RestController
@RequestMapping("/api/placements")
class PlacementController(
    private val placements: PlacementQueryAppAction,
    private val jdStorage: PlacementJdStorageService,
    private val experiences: PlacementExperienceQueryAppAction,
    private val submissions: ExperienceSubmissionAppAction,
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    /** Season summary plus every company, latest activity first. */
    @GetMapping
    fun list(@AuthenticationPrincipal user: OAuth2User?): ResponseEntity<PlacementListResponse> {
        requireStudent(user)
        return ResponseEntity.ok(placements.list())
    }

    /** Seniors' interview experiences without their text, optionally for one company, and the count per company. */
    @GetMapping("/experiences")
    fun experiences(@AuthenticationPrincipal user: OAuth2User?, @RequestParam(required = false) company: String?): ResponseEntity<ExperienceListResponse> {
        requireStudent(user)
        return ResponseEntity.ok(experiences.list(company?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }))
    }

    /**
     * Sends in an interview experience: a Google Docs / Drive link, typed text, or an uploaded .md / .txt file (read
     * by the app and sent as text). Kept with the sender's name for checking; published later without it.
     */
    @PostMapping("/experiences/submissions")
    fun submitExperience(@AuthenticationPrincipal user: OAuth2User?, @RequestBody req: ExperienceSubmissionRequest): ResponseEntity<Any> {
        requireStudent(user)
        val email = user!!.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email) ?: return ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        return try {
            ResponseEntity.status(201).body(submissions.submit(student, email, req))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Couldn't send it.")))
        }
    }

    /** The caller's own submissions and where each one is (PENDING, PUBLISHED, REJECTED). */
    @GetMapping("/experiences/submissions/mine")
    fun mySubmissions(@AuthenticationPrincipal user: OAuth2User?): ResponseEntity<Any> {
        requireStudent(user)
        val student = studentRepositoryAppAction.findByEmail(user!!.getAttribute<String>("email") ?: "")
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Student not found"))
        return ResponseEntity.ok(submissions.mine(student.id ?: 0))
    }

    /** One interview experience in full (Markdown). Anonymous: no names or roll numbers are stored. */
    @GetMapping("/experiences/{id}")
    fun experience(@AuthenticationPrincipal user: OAuth2User?, @PathVariable id: String): ResponseEntity<DMPlacementExperience> {
        requireStudent(user)
        return experiences.find(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    /** One company in full: each drive's roles, CTC, stipend, eligibility and job description, and who was selected. */
    @GetMapping("/{id}")
    fun company(@AuthenticationPrincipal user: OAuth2User?, @PathVariable id: String): ResponseEntity<DMPlacementCompany> {
        requireStudent(user)
        return placements.find(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    /**
     * A drive's job description PDF as the company sent it. Only files the season data lists as a PDF of that
     * drive are served, so the path can never reach anything else in the bucket.
     */
    @GetMapping("/jd/{eventId}/{file}")
    fun jdPdf(@AuthenticationPrincipal user: OAuth2User?, @PathVariable eventId: Int, @PathVariable file: String): ResponseEntity<ByteArray> {
        requireStudent(user)
        val listed = placements.all().asSequence().flatMap { it.drives.asSequence() }
            .filter { it.eventId == eventId }
            .flatMap { it.jdFiles.asSequence() }
            .any { it.pdf && it.original == file }
        if (!listed) return ResponseEntity.notFound().build()
        val bytes = jdStorage.read(eventId, file) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
            .header("Content-Disposition", ContentDisposition.inline().filename(file).build().toString())
            .body(bytes)
    }

    private fun requireStudent(user: OAuth2User?) {
        if (user == null) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val email = user.getAttribute<String>("email")
        if (!PlacementAccess.allowed(email, DemoUserUtil.isDemoUser(user))) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, PlacementAccess.DENIED)
        }
    }
}
