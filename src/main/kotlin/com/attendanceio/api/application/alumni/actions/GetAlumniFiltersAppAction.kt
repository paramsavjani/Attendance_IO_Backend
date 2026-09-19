package com.attendanceio.api.application.alumni.actions

import com.attendanceio.api.model.alumni.AlumniFiltersResponse
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class GetAlumniFiltersAppAction(
    private val alumniRepositoryAppAction: AlumniRepositoryAppAction
) {
    /** Cached: the data only changes when the app is redeployed with a new alumni.json. */
    @Cacheable("alumniFilters")
    fun execute(): AlumniFiltersResponse = AlumniFiltersResponse(
        batches = alumniRepositoryAppAction.distinctBatches(),
        courses = alumniRepositoryAppAction.coursesByPopularity(),
        cities = alumniRepositoryAppAction.topCities(30),
        totalAlumni = alumniRepositoryAppAction.countAlumni(),
        totalCompanies = alumniRepositoryAppAction.countCompanies()
    )
}
