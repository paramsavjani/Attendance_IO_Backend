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
    
    /**
     * Find timetable entries with eagerly fetched relationships (subject, day, slot)
     */
    fun findByStudentIdAndSemesterIdWithDetails(studentId: Long, semesterId: Long): List<DMStudentTimetable> {
        return studentTimetableRepository.findByStudentIdAndSemesterIdWithDetails(studentId, semesterId)
    }
    
    /**
     * Find specific timetable entry by student, semester, day, and slot
     */
    fun findByStudentIdAndSemesterIdAndDayIdAndSlotId(
        studentId: Long, 
        semesterId: Long, 
        dayId: Short, 
        slotId: Short
    ): DMStudentTimetable? {
        return studentTimetableRepository.findByStudentIdAndSemesterIdAndDayIdAndSlotId(
            studentId, semesterId, dayId, slotId
        )
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
    
    /**
     * Delete timetable entries for specific subjects (used when subjects are unenrolled)
     * @return Number of entries deleted
     */
    @Transactional
    fun deleteAllByStudentIdAndSemesterIdAndSubjectIds(
        studentId: Long, 
        semesterId: Long, 
        subjectIds: List<Long>
    ): Int {
        if (subjectIds.isEmpty()) return 0
        return studentTimetableRepository.deleteAllByStudentIdAndSemesterIdAndSubjectIdIn(
            studentId, semesterId, subjectIds
        )
    }
}

