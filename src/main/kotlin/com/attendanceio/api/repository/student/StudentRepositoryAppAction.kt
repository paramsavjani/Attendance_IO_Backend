package com.attendanceio.api.repository.student

import com.attendanceio.api.model.student.DMStudent
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
        val normalizedQuery = query.trim().replace(Regex("\\s+"), " ")
        if (normalizedQuery.isBlank()) return emptyList()

        return studentRepository.searchByNameFlexible(normalizedQuery, limit)
    }
    
    fun searchBySid(query: String, limit: Int = 10): List<DMStudent> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

        return if (limit == 10) {
            studentRepository.findTop10BySidContainingIgnoreCase(normalizedQuery)
        } else {
            // Fallback for other limits (though we always use 10)
            studentRepository.findBySidContainingIgnoreCase(normalizedQuery).take(limit)
        }
    }
    
    fun findAllWithFcmToken(): List<DMStudent> {
        return studentRepository.findByFcmTokenIsNotNull()
    }

    fun findStudentsForDailyReminderAtHour(hour: Int): List<DMStudent> = when (hour) {
        18 -> studentRepository.findStudentsForDailyReminderAt18()
        20 -> studentRepository.findStudentsForDailyReminderAt20()
        22 -> studentRepository.findStudentsForDailyReminderAt22()
        else -> emptyList()
    }
}