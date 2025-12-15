package com.attendanceio.api.application.search.actions

import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class SearchStudentsAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    private val SEARCH_LIMIT = 10
    
    fun execute(query: String): List<Map<String, Any>> {
        if (query.isBlank()) {
            return emptyList()
        }

        val byName = studentRepositoryAppAction.searchByName(query, SEARCH_LIMIT)
        val bySid = studentRepositoryAppAction.searchBySid(query, SEARCH_LIMIT)
        
        // Combine and deduplicate by ID (limit already applied at DB level)
        val allStudents = (byName + bySid).distinctBy { it.id }.take(SEARCH_LIMIT)
        
        return allStudents.mapNotNull { student ->
            val studentId = student.id ?: return@mapNotNull null
            mapOf(
                "id" to studentId.toString(),
                "name" to (student.name ?: ""),
                "rollNumber" to student.sid,
                "email" to (student.email ?: ""),
                "pictureUrl" to (student.pictureUrl ?: "")
            )
        }
    }
}

