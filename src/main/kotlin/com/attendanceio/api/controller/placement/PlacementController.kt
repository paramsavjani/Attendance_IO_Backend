package com.attendanceio.api.controller.placement

import com.attendanceio.api.application.placement.PlacementAccess
import com.attendanceio.api.application.placement.PlacementQueryAppAction
import com.attendanceio.api.util.DemoUserUtil
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.server.ResponseStatusException
import com.attendanceio.api.model.college.DMPlacementCompany
import com.attendanceio.api.model.placement.PlacementListResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** This season's placement drives and results. DAU students only (see [PlacementAccess]): it names selected students. */
@RestController
@RequestMapping("/api/placements")
class PlacementController(
    private val placements: PlacementQueryAppAction
) {
    /** Season summary plus every company, latest activity first. */
    @GetMapping
    fun list(@AuthenticationPrincipal user: OAuth2User?): ResponseEntity<PlacementListResponse> {
        requireStudent(user)
        return ResponseEntity.ok(placements.list())
    }

    /** One company in full: each drive's roles, CTC, stipend, eligibility and job description, and who was selected. */
    @GetMapping("/{id}")
    fun company(@AuthenticationPrincipal user: OAuth2User?, @PathVariable id: String): ResponseEntity<DMPlacementCompany> {
        requireStudent(user)
        return placements.find(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    private fun requireStudent(user: OAuth2User?) {
        if (user == null) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val email = user.getAttribute<String>("email")
        if (!PlacementAccess.allowed(email, DemoUserUtil.isDemoUser(user))) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, PlacementAccess.DENIED)
        }
    }
}
