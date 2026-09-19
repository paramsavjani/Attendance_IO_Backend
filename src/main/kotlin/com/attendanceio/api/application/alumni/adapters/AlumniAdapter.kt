package com.attendanceio.api.application.alumni.adapters

import com.attendanceio.api.model.alumni.AlumniCompanySummaryResponse
import com.attendanceio.api.model.alumni.AlumniPageResponse
import com.attendanceio.api.model.alumni.AlumniResponse
import com.attendanceio.api.model.alumni.DMAlumni
import com.attendanceio.api.model.alumni.DMAlumniCompany
import org.springframework.data.domain.Page

fun DMAlumniCompany.toSummary() = AlumniCompanySummaryResponse(
    id = id!!,
    name = name,
    avgLpa = avgLpa,
    alumniCount = alumniCount
)

fun DMAlumni.toResponse() = AlumniResponse(
    id = id!!,
    name = name,
    headline = headline,
    title = title,
    city = city,
    batch = batch,
    course = course,
    linkedinUrl = linkedinUrl,
    almaconnectUrl = almaconnectUrl,
    company = company.toSummary()
)

fun <E : Any, R> Page<E>.toPageResponse(map: (E) -> R) = AlumniPageResponse(
    items = content.map(map),
    page = number,
    size = size,
    totalItems = totalElements,
    totalPages = totalPages
)
