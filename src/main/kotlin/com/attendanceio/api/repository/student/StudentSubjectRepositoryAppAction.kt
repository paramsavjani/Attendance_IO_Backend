package com.attendanceio.api.repository.student

import com.attendanceio.api.model.student.DMStudentSubject
import org.springframework.stereotype.Component

@Component
class StudentSubjectRepositoryAppAction(
    private val studentSubjectRepository: StudentSubjectRepository
) {
    fun findByStudentId(studentId: Long): List<DMStudentSubject> {
        return studentSubjectRepository.findByStudentId(studentId)
    }
    
    fun findBySubjectId(subjectId: Long): List<DMStudentSubject> {
        return studentSubjectRepository.findBySubjectId(subjectId)
    }
    
    fun findByStudentIdAndSubjectId(studentId: Long, subjectId: Long): DMStudentSubject? {
        return studentSubjectRepository.findByStudentIdAndSubjectId(studentId, subjectId)
    }
    
    fun save(studentSubject: DMStudentSubject): DMStudentSubject {
        return studentSubjectRepository.save(studentSubject)
    }
    
    fun delete(studentSubject: DMStudentSubject) {
        studentSubjectRepository.delete(studentSubject)
    }
    
    fun deleteAll(studentSubjects: List<DMStudentSubject>) {
        studentSubjectRepository.deleteAll(studentSubjects)
    }
}

