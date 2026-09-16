package com.attendanceio.api.application.search.actions

import com.attendanceio.api.application.search.adapters.StudentSearchAdapter
import com.attendanceio.api.model.search.StudentSearchResponse
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class SearchStudentsAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentSearchAdapter: StudentSearchAdapter
) {
    private val SEARCH_LIMIT = 10
    
    fun execute(query: String): List<StudentSearchResponse> {
        val normalizedQuery = query.trim().replace(Regex("\\s+"), " ")
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        val byName = studentRepositoryAppAction.searchByName(normalizedQuery, SEARCH_LIMIT)
        val bySid = studentRepositoryAppAction.searchBySid(normalizedQuery, SEARCH_LIMIT)
        
        // Combine and deduplicate by ID (limit already applied at DB level)
        val allStudents = (byName + bySid).distinctBy { it.id }.take(SEARCH_LIMIT)
        
        val summaries = attendanceRepositoryAppAction
            .summarizeActiveSemesterMarkedAttendance(allStudents.mapNotNull { it.id })

        return studentSearchAdapter.toResponseList(allStudents, summaries)
    }
}

