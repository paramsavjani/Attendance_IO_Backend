package com.attendanceio.api.repository.subject

import com.attendanceio.api.model.subject.DMSubject
import org.springframework.stereotype.Component

@Component
class SubjectRepositoryAppAction(
    private val subjectRepository: SubjectRepository
) {
    fun findByCode(code: String): DMSubject? {
        return subjectRepository.findByCode(code)
    }
    
    fun findBySemesterId(semesterId: Long): List<DMSubject> {
        return subjectRepository.findBySemesterId(semesterId)
    }
    
    fun findByCodeAndSemesterId(code: String, semesterId: Long): DMSubject? {
        return subjectRepository.findByCodeAndSemesterId(code, semesterId)
    }
}
