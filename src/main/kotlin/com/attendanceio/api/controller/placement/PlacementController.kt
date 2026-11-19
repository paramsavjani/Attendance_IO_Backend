package com.attendanceio.api.controller.placement

import com.attendanceio.api.application.placement.PlacementQueryAppAction
import com.attendanceio.api.model.college.DMPlacementCompany
import com.attendanceio.api.model.placement.PlacementListResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** This season's placement drives and results. Signed-in users only: it names selected students. */
@RestController
@RequestMapping("/api/placements")
class PlacementController(
    private val placements: PlacementQueryAppAction
) {
    /** Season summary plus every company, latest activity first. */
    @GetMapping
    fun list(): ResponseEntity<PlacementListResponse> = ResponseEntity.ok(placements.list())

    /** One company in full: each drive's roles, CTC, stipend, eligibility and job description, and who was selected. */
    @GetMapping("/{id}")
    fun company(@PathVariable id: String): ResponseEntity<DMPlacementCompany> =
        placements.find(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}
