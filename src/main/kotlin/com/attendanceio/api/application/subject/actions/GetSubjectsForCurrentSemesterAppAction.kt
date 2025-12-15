package com.attendanceio.api.application.subject.actions

import com.attendanceio.api.application.subject.adapters.SubjectAdapter
import com.attendanceio.api.model.subject.SubjectResponse
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetSubjectsForCurrentSemesterAppAction(
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val subjectAdapter: SubjectAdapter
) {
    fun execute(): List<SubjectResponse> {
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        
        if (activeSemesters.isEmpty()) {
            return emptyList()
        }
        
        val currentSemester = activeSemesters.first()
        val semesterId = currentSemester.id ?: return emptyList()
        
        // Get all subjects for current semester
        val subjects = subjectRepositoryAppAction.findBySemesterId(semesterId)
        
        // Convert to response
        return subjectAdapter.toResponseList(subjects)
    }
}
