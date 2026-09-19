package com.attendanceio.api.repository.alumni

import com.attendanceio.api.model.alumni.DMAlumni
import com.attendanceio.api.model.alumni.DMAlumniCompany
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class AlumniRepositoryAppAction(
    private val alumniRepository: AlumniRepository,
    private val alumniCompanyRepository: AlumniCompanyRepository
) {
    fun findById(id: Long): DMAlumni? = alumniRepository.findById(id).orElse(null)

    fun findCompanyById(id: Long): DMAlumniCompany? = alumniCompanyRepository.findById(id).orElse(null)

    fun findByCompany(companyId: Long): List<DMAlumni> = alumniRepository.findByCompanyIdOrderByBatchDescNameAsc(companyId)

    fun search(
        query: String?,
        companyId: Long?,
        batch: Int?,
        course: String?,
        city: String?,
        linkedinOnly: Boolean,
        page: Int,
        size: Int
    ): Page<DMAlumni> = alumniRepository.search(
        query?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() },
        companyId,
        batch,
        course?.trim()?.takeIf { it.isNotBlank() },
        city?.trim()?.takeIf { it.isNotBlank() },
        linkedinOnly,
        PageRequest.of(page, size)
    )

    fun searchCompanies(query: String?, sort: String, page: Int, size: Int): Page<DMAlumniCompany> =
        alumniCompanyRepository.search(query?.trim()?.takeIf { it.isNotBlank() }, sort, PageRequest.of(page, size))

    fun distinctBatches(): List<Int> = alumniRepository.distinctBatches()

    fun coursesByPopularity(): List<String> = alumniRepository.coursesByPopularity()

    fun topCities(limit: Int): List<String> = alumniRepository.topCities(limit)

    fun countAlumni(): Long = alumniRepository.count()

    fun countCompanies(): Long = alumniCompanyRepository.count()

    // --- used by the startup sync ---

    fun allCompanies(): List<DMAlumniCompany> = alumniCompanyRepository.findAll()

    fun allAlumni(): List<DMAlumni> = alumniRepository.findAll()

    fun saveCompanies(companies: Collection<DMAlumniCompany>): List<DMAlumniCompany> = alumniCompanyRepository.saveAll(companies)

    fun saveAlumni(alumni: Collection<DMAlumni>): List<DMAlumni> = alumniRepository.saveAll(alumni)
}
