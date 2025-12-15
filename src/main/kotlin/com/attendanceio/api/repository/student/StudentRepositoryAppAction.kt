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
}