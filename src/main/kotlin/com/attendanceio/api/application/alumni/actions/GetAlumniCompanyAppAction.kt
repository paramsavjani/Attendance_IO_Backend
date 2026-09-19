package com.attendanceio.api.application.alumni.actions

import com.attendanceio.api.application.alumni.adapters.toResponse
import com.attendanceio.api.application.alumni.adapters.toSummary
import com.attendanceio.api.model.alumni.AlumniCompanyDetailResponse
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetAlumniCompanyAppAction(
    private val alumniRepositoryAppAction: AlumniRepositoryAppAction
) {
    fun execute(companyId: Long): AlumniCompanyDetailResponse? {
        val company = alumniRepositoryAppAction.findCompanyById(companyId) ?: return null
        val alumni = alumniRepositoryAppAction.findByCompany(companyId)
        return AlumniCompanyDetailResponse(
            company = company.toSummary(),
            alumni = alumni.map { it.toResponse() }
        )
    }
}
