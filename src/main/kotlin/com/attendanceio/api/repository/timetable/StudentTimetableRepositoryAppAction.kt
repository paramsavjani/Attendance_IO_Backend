package com.attendanceio.api.repository.timetable

import com.attendanceio.api.model.timetable.DMStudentTimetable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StudentTimetableRepositoryAppAction(
    private val studentTimetableRepository: StudentTimetableRepository
) {
    fun findByStudentId(studentId: Long): List<DMStudentTimetable> {
        return studentTimetableRepository.findByStudentId(studentId)
    }
    
    fun findByStudentIdAndSemesterId(studentId: Long, semesterId: Long): List<DMStudentTimetable> {
        return studentTimetableRepository.findByStudentIdAndSemesterId(studentId, semesterId)
    }
    
    fun save(studentTimetable: DMStudentTimetable): DMStudentTimetable {
        return studentTimetableRepository.save(studentTimetable)
    }
    
    fun saveAll(studentTimetables: List<DMStudentTimetable>): List<DMStudentTimetable> {
        return studentTimetableRepository.saveAll(studentTimetables)
    }
    
    @Transactional
    fun deleteAllByStudentIdAndSemesterId(studentId: Long, semesterId: Long) {
        studentTimetableRepository.deleteAllByStudentIdAndSemesterId(studentId, semesterId)
    }
}

