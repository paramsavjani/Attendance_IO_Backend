package com.attendanceio.api.application.search.actions

import com.attendanceio.api.model.search.DMSearchHistory
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.search.SearchHistoryRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class SaveSearchHistoryAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val searchHistoryRepositoryAppAction: SearchHistoryRepositoryAppAction
) {
    fun execute(student: DMStudent, viewedStudentId: Long): DMSearchHistory {
        val viewedStudent = studentRepositoryAppAction.findById(viewedStudentId)
            ?: throw NoSuchElementException("Student not found: $viewedStudentId")

        val studentId = student.id ?: throw IllegalStateException("Student has no ID")
        val existing = searchHistoryRepositoryAppAction.findByStudentIdAndViewedStudentId(studentId, viewedStudentId)
        if (existing != null) {
            existing.isDeleted = true
            searchHistoryRepositoryAppAction.save(existing)
        }

        return searchHistoryRepositoryAppAction.save(DMSearchHistory().apply {
            this.student = student
            this.viewedStudent = viewedStudent
            this.isDeleted = false
        })
    }
}
