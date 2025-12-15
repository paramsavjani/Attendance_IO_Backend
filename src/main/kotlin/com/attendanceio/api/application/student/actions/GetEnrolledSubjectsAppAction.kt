package com.attendanceio.api.application.student.actions

import com.attendanceio.api.application.student.adapters.EnrolledSubjectAdapter
import com.attendanceio.api.model.student.EnrolledSubjectResponse
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetEnrolledSubjectsAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val enrolledSubjectAdapter: EnrolledSubjectAdapter
) {
    fun execute(studentId: Long): List<EnrolledSubjectResponse> {
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")
        
        val enrolledSubjects = studentSubjectRepositoryAppAction.findByStudentId(studentId)
        
        return enrolledSubjectAdapter.toResponseList(enrolledSubjects)
    }
}

