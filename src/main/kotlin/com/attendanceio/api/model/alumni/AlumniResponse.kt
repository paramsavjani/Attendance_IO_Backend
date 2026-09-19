package com.attendanceio.api.model.alumni

import java.math.BigDecimal

data class AlumniResponse(
    val id: Long,
    val name: String,
    val headline: String?,
    val title: String?,
    val city: String?,
    val batch: Int?,
    val course: String?,
    val linkedinUrl: String?,
    val almaconnectUrl: String,
    val company: AlumniCompanySummaryResponse
)

data class AlumniCompanySummaryResponse(
    val id: Long,
    val name: String,
    val avgLpa: BigDecimal?,
    val alumniCount: Int
)

/** A company with everyone who works there. */
data class AlumniCompanyDetailResponse(
    val company: AlumniCompanySummaryResponse,
    val alumni: List<AlumniResponse>
)

data class AlumniPageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int
)

/** Values the client can offer as filter chips. */
data class AlumniFiltersResponse(
    val batches: List<Int>,
    val courses: List<String>,
    val cities: List<String>,
    val totalAlumni: Long,
    val totalCompanies: Long
)
