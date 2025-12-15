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
        // Verify student exists
        studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")
        
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            return emptyList()
        }
        
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: return emptyList()
        
        // Get all enrolled subjects for the student
        val enrolledSubjects = studentSubjectRepositoryAppAction.findByStudentId(studentId)
        
        // Filter to only include subjects from the current semester
        val currentSemesterSubjects = enrolledSubjects.filter { studentSubject ->
            studentSubject.subject?.semester?.id == currentSemesterId
        }
        
        return enrolledSubjectAdapter.toResponseList(currentSemesterSubjects)
    }
}

