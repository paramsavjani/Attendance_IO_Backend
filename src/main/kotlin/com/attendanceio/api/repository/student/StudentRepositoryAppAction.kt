package com.attendanceio.api.repository.student

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepository
import org.springframework.stereotype.Component

@Component
class StudentRepositoryAppAction(
    private val studentRepository: StudentRepository
){
    fun findByEmail(email: String): DMStudent? = studentRepository.findByEmail(email)

    fun findBySid(sid: String): DMStudent? = studentRepository.findBySid(sid)

    fun create(student: DMStudent): DMStudent {
        return studentRepository.save(student)
    }
    
    fun update(student: DMStudent): DMStudent {
        return studentRepository.save(student)
    }
    
    fun findById(studentId: Long): DMStudent? {
        val optional = studentRepository.findById(studentId)
        return if (optional.isPresent) optional.get() else null
    }
    
    fun searchByName(query: String, limit: Int = 10): List<DMStudent> {
        return if (limit == 10) {
            studentRepository.findTop10ByNameContainingIgnoreCase(query)
        } else {
            // Fallback for other limits (though we always use 10)
            studentRepository.findByNameContainingIgnoreCase(query).take(limit)
        }
    }
    
    fun searchBySid(query: String, limit: Int = 10): List<DMStudent> {
        return if (limit == 10) {
            studentRepository.findTop10BySidContainingIgnoreCase(query)
        } else {
            // Fallback for other limits (though we always use 10)
            studentRepository.findBySidContainingIgnoreCase(query).take(limit)
        }
    }
    
    fun findAllWithFcmToken(): List<DMStudent> {
        return studentRepository.findByFcmTokenIsNotNull()
    }
}