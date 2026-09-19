package com.attendanceio.api.application.alumni.actions

import com.attendanceio.api.application.alumni.adapters.toPageResponse
import com.attendanceio.api.application.alumni.adapters.toSummary
import com.attendanceio.api.model.alumni.AlumniCompanySummaryResponse
import com.attendanceio.api.model.alumni.AlumniPageResponse
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class SearchAlumniCompaniesAppAction(
    private val alumniRepositoryAppAction: AlumniRepositoryAppAction
) {
    /** [sort] is "lpa" or "count" (default). */
    fun execute(query: String?, sort: String?, page: Int, size: Int): AlumniPageResponse<AlumniCompanySummaryResponse> {
        val sortKey = if (sort.equals("lpa", ignoreCase = true)) "lpa" else "count"
        return alumniRepositoryAppAction
            .searchCompanies(query, sortKey, page.coerceAtLeast(0), size.coerceIn(1, SearchAlumniAppAction.MAX_PAGE_SIZE))
            .toPageResponse { it.toSummary() }
    }
}
