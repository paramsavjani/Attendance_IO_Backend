package com.attendanceio.api.controller.alumni

import com.attendanceio.api.application.alumni.actions.GetAlumniAppAction
import com.attendanceio.api.application.alumni.actions.GetAlumniCompanyAppAction
import com.attendanceio.api.application.alumni.actions.GetAlumniFiltersAppAction
import com.attendanceio.api.application.alumni.actions.SearchAlumniAppAction
import com.attendanceio.api.application.alumni.actions.SearchAlumniCompaniesAppAction
import com.attendanceio.api.model.alumni.AlumniCompanyDetailResponse
import com.attendanceio.api.model.alumni.AlumniCompanySummaryResponse
import com.attendanceio.api.model.alumni.AlumniFiltersResponse
import com.attendanceio.api.model.alumni.AlumniPageResponse
import com.attendanceio.api.model.alumni.AlumniResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Alumni directory (AlmaConnect export). Signed-in users only — LinkedIn links are contact data. */
@RestController
@RequestMapping("/api/alumni")
class AlumniController(
    private val searchAlumniAppAction: SearchAlumniAppAction,
    private val getAlumniAppAction: GetAlumniAppAction,
    private val searchAlumniCompaniesAppAction: SearchAlumniCompaniesAppAction,
    private val getAlumniCompanyAppAction: GetAlumniCompanyAppAction,
    private val getAlumniFiltersAppAction: GetAlumniFiltersAppAction
) {
    /**
     * `q` matches name, company, headline, title or city; the other params are exact filters.
     * Results are ranked name → company → other matches, then by company pay and batch.
     */
    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) companyId: Long?,
        @RequestParam(required = false) batch: Int?,
        @RequestParam(required = false) course: String?,
        @RequestParam(required = false) city: String?,
        @RequestParam(defaultValue = "false") linkedinOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<AlumniPageResponse<AlumniResponse>> =
        ResponseEntity.ok(searchAlumniAppAction.execute(q, companyId, batch, course, city, linkedinOnly, page, size))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<AlumniResponse> =
        getAlumniAppAction.execute(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** Companies with alumni; `sort=count` (default) or `sort=lpa`. */
    @GetMapping("/companies")
    fun companies(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<AlumniPageResponse<AlumniCompanySummaryResponse>> =
        ResponseEntity.ok(searchAlumniCompaniesAppAction.execute(q, sort, page, size))

    @GetMapping("/companies/{id}")
    fun company(@PathVariable id: Long): ResponseEntity<AlumniCompanyDetailResponse> =
        getAlumniCompanyAppAction.execute(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    /** Batches, courses, top cities and totals for filter chips. */
    @GetMapping("/filters")
    fun filters(): ResponseEntity<AlumniFiltersResponse> = ResponseEntity.ok(getAlumniFiltersAppAction.execute())
}
