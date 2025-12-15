package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.student.DMStudentSubject
import com.attendanceio.api.model.student.SaveEnrolledSubjectsRequest
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class SaveEnrolledSubjectsAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction
) {
    private val MAX_SUBJECTS = 7
    
    fun execute(studentId: Long, request: SaveEnrolledSubjectsRequest): List<String> {
        // Validate max subjects constraint
        if (request.subjectIds.size > MAX_SUBJECTS) {
            throw IllegalArgumentException("Maximum $MAX_SUBJECTS subjects allowed. You selected ${request.subjectIds.size} subjects.")
        }
        
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found")
        
        // Get current enrollments
        val currentEnrollments = studentSubjectRepositoryAppAction.findByStudentId(studentId)
        
        // Delete all current enrollments
        if (currentEnrollments.isNotEmpty()) {
            studentSubjectRepositoryAppAction.deleteAll(currentEnrollments)
        }
        
        // Create new enrollments
        val savedSubjectIds = mutableListOf<String>()
        for (subjectIdStr in request.subjectIds) {
            val subjectId = subjectIdStr.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid subject ID: $subjectIdStr")
            
            val subject = subjectRepositoryAppAction.findById(subjectId)
                ?: throw IllegalArgumentException("Subject not found: $subjectId")
                
                val studentSubject = DMStudentSubject().apply {
                    this.student = student
                    this.subject = subject
                }
            studentSubjectRepositoryAppAction.save(studentSubject)
            savedSubjectIds.add(subjectIdStr)
        }
        
        return savedSubjectIds
    }
}

