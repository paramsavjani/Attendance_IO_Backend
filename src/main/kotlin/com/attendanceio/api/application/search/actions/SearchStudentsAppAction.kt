package com.attendanceio.api.application.search.actions

import com.attendanceio.api.application.placement.PlacementQueryAppAction
import com.attendanceio.api.application.search.adapters.StudentSearchAdapter
import com.attendanceio.api.model.search.StudentSearchResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class SearchStudentsAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentSearchAdapter: StudentSearchAdapter,
    private val placements: PlacementQueryAppAction
) {
    private val SEARCH_LIMIT = 10
    private val MAX_OFFSET = 500

    /**
     * One page of matches: name matches first, then roll-number matches. Both lists are fetched up to the end of
     * the page, so page N always continues page N-1 exactly.
     */
    fun execute(query: String, offset: Int = 0, withPlacements: Boolean = false): List<StudentSearchResponse> {
        val normalizedQuery = query.trim().replace(Regex("\\s+"), " ")
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        val start = offset.coerceIn(0, MAX_OFFSET)
        val end = start + SEARCH_LIMIT
        val byName = studentRepositoryAppAction.searchByName(normalizedQuery, end)
        val bySid = studentRepositoryAppAction.searchBySid(normalizedQuery, end)

        val allStudents = (byName + bySid).distinctBy { it.id }.drop(start).take(SEARCH_LIMIT)
        if (allStudents.isEmpty()) return emptyList()
        
        val summaries = attendanceRepositoryAppAction
            .summarizeActiveSemesterMarkedAttendance(allStudents.mapNotNull { it.id })

        val results = studentSearchAdapter.toResponseList(allStudents, summaries)
        if (!withPlacements) return results
        val offers = runCatching { placements.bestOffers(results.map { it.rollNumber }) }.getOrDefault(emptyMap())
        return results.map { r -> offers[r.rollNumber.trim()]?.let { r.copy(placement = it) } ?: r }
    }
}

