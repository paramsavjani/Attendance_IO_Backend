package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.student.UpdateMinimumCriteriaRequest
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdateMinimumCriteriaAppAction(
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction
) {
    @Transactional
    fun execute(student: DMStudent, request: UpdateMinimumCriteriaRequest) {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        
        val subjectId = request.subjectId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid subject ID: ${request.subjectId}")
        
        // Validate minimum criteria if provided (should be between 0 and 100)
        if (request.minimumCriteria != null) {
            if (request.minimumCriteria < 0 || request.minimumCriteria > 100) {
                throw IllegalArgumentException("Minimum criteria must be between 0 and 100")
            }
        }
        
        val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            ?: throw IllegalArgumentException("Subject not found in enrolled subjects")
        
        studentSubject.minimumCriteria = request.minimumCriteria
        studentSubjectRepositoryAppAction.save(studentSubject)
    }
}

