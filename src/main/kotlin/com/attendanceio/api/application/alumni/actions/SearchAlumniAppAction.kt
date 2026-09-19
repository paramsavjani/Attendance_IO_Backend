package com.attendanceio.api.application.alumni.actions

import com.attendanceio.api.application.alumni.adapters.toPageResponse
import com.attendanceio.api.application.alumni.adapters.toResponse
import com.attendanceio.api.model.alumni.AlumniPageResponse
import com.attendanceio.api.model.alumni.AlumniResponse
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SearchAlumniAppAction(
    private val alumniRepositoryAppAction: AlumniRepositoryAppAction
) {
    companion object {
        const val MAX_PAGE_SIZE = 50
    }

    @Transactional(readOnly = true) // company is lazy; map it while the session is open
    fun execute(
        query: String?,
        companyId: Long?,
        batch: Int?,
        course: String?,
        city: String?,
        linkedinOnly: Boolean,
        page: Int,
        size: Int
    ): AlumniPageResponse<AlumniResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return alumniRepositoryAppAction
            .search(query, companyId, batch, course, city, linkedinOnly, safePage, safeSize)
            .toPageResponse { it.toResponse() }
    }
}
