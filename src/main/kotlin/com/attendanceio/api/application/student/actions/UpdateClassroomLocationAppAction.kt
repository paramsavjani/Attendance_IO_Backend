package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.student.UpdateClassroomLocationRequest
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdateClassroomLocationAppAction(
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction
) {
    @Transactional
    fun execute(student: DMStudent, request: UpdateClassroomLocationRequest) {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        
        val subjectId = request.subjectId.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid subject ID: ${request.subjectId}")
        
        val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            ?: throw IllegalArgumentException("Subject not enrolled for this student")
        
        studentSubject.classroomLocation = request.classroomLocation
        studentSubjectRepositoryAppAction.save(studentSubject)
    }
}

