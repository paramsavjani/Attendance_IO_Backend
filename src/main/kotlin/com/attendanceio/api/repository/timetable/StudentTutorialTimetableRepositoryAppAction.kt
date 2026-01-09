package com.attendanceio.api.repository.timetable

import com.attendanceio.api.model.timetable.DMStudentTutorialTimetable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StudentTutorialTimetableRepositoryAppAction(
    private val studentTutorialTimetableRepository: StudentTutorialTimetableRepository
) {
    fun findByStudentIdAndSemesterId(studentId: Long, semesterId: Long): List<DMStudentTutorialTimetable> {
        return studentTutorialTimetableRepository.findByStudentIdAndSemesterId(studentId, semesterId)
    }
    
    fun save(tutorialTimetable: DMStudentTutorialTimetable): DMStudentTutorialTimetable {
        return studentTutorialTimetableRepository.save(tutorialTimetable)
    }
    
    fun saveAll(tutorialTimetables: List<DMStudentTutorialTimetable>): List<DMStudentTutorialTimetable> {
        return studentTutorialTimetableRepository.saveAll(tutorialTimetables)
    }
    
    @Transactional
    fun deleteByStudentIdAndSemesterId(studentId: Long, semesterId: Long) {
        studentTutorialTimetableRepository.deleteByStudentIdAndSemesterId(studentId, semesterId)
    }
}
