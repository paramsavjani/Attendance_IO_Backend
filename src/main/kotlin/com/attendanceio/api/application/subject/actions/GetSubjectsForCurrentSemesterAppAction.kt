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
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) return emptyList()
        val semesterId = activeSemesters.first().id ?: return emptyList()
        return subjectAdapter.toResponseList(subjectRepositoryAppAction.findBySemesterId(semesterId))
    }
}
