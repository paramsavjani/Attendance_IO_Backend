package com.attendanceio.api.application.student.actions

import com.attendanceio.api.application.student.adapters.EnrolledSubjectAdapter
import com.attendanceio.api.model.student.EnrolledSubjectResponse
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetEnrolledSubjectsAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val enrolledSubjectAdapter: EnrolledSubjectAdapter
) {
    fun execute(studentId: Long): List<EnrolledSubjectResponse> {
        studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")

        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) return emptyList()

        val currentSemesterId = activeSemesters.first().id ?: return emptyList()
        val currentSemesterSubjects = studentSubjectRepositoryAppAction.findByStudentId(studentId)
            .filter { it.subject?.semester?.id == currentSemesterId }

        return enrolledSubjectAdapter.toResponseList(currentSemesterSubjects)
    }
}
