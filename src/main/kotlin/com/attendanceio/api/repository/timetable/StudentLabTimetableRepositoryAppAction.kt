package com.attendanceio.api.repository.timetable

import com.attendanceio.api.model.timetable.DMStudentLabTimetable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StudentLabTimetableRepositoryAppAction(
    private val studentLabTimetableRepository: StudentLabTimetableRepository
) {
    fun findByStudentIdAndSemesterId(studentId: Long, semesterId: Long): List<DMStudentLabTimetable> {
        return studentLabTimetableRepository.findByStudentIdAndSemesterId(studentId, semesterId)
    }
    
    fun save(labTimetable: DMStudentLabTimetable): DMStudentLabTimetable {
        return studentLabTimetableRepository.save(labTimetable)
    }
    
    fun saveAll(labTimetables: List<DMStudentLabTimetable>): List<DMStudentLabTimetable> {
        return studentLabTimetableRepository.saveAll(labTimetables)
    }
    
    @Transactional
    fun deleteByStudentIdAndSemesterId(studentId: Long, semesterId: Long) {
        studentLabTimetableRepository.deleteByStudentIdAndSemesterId(studentId, semesterId)
    }
}
