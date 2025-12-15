package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.student.DMStudentSubject
import com.attendanceio.api.model.student.SaveEnrolledSubjectsRequest
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SaveEnrolledSubjectsAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction
) {
    private val MAX_SUBJECTS = 7
    
    @Transactional
    fun execute(student: DMStudent, request: SaveEnrolledSubjectsRequest): List<String> {
        // Validate max subjects constraint
        if (request.subjectIds.size > MAX_SUBJECTS) {
            throw IllegalArgumentException("Maximum $MAX_SUBJECTS subjects allowed. You selected ${request.subjectIds.size} subjects.")
        }
        
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        
        // Convert subject IDs to Long and validate format
        val subjectIds = request.subjectIds.mapNotNull { subjectIdStr ->
            subjectIdStr.toLongOrNull() ?: throw IllegalArgumentException("Invalid subject ID: $subjectIdStr")
        }
        
        // DB Call 1: Fetch all subjects in a single query (optimized: 1 query instead of N)
        val subjects = subjectRepositoryAppAction.findAllById(subjectIds)
        
        // Validate all subjects exist
        if (subjects.size != subjectIds.size) {
            val foundSubjectIds = subjects.map { it.id!! }.toSet()
            val missingSubjectIds = subjectIds.filter { it !in foundSubjectIds }
            throw IllegalArgumentException("Subject(s) not found: ${missingSubjectIds.joinToString(", ")}")
        }
        
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            throw IllegalArgumentException("No active semester found")
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: throw IllegalArgumentException("Current semester ID is null")
        
        // DB Call 2: Delete only enrollments from current semester (not all enrollments)
        studentSubjectRepositoryAppAction.deleteAllByStudentIdAndSemesterId(studentId, currentSemesterId)
        
        // Create all new enrollments in memory
        val newEnrollments = subjects.map { subject ->
            DMStudentSubject().apply {
                this.student = student
                this.subject = subject
            }
        }
        
        // DB Call 3: Save all enrollments in a single batch operation (optimized: 1 query instead of N)
        studentSubjectRepositoryAppAction.saveAll(newEnrollments)
        
        // Total: Only 3 database calls regardless of number of subjects
        return request.subjectIds
    }
}

