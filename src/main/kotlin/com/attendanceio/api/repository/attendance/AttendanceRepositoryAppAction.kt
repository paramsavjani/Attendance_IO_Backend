package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.DMAttendance
import org.springframework.stereotype.Component

@Component
class AttendanceRepositoryAppAction(
    private val attendanceRepository: AttendanceRepository
) {
    fun findByStudentIdAndSubjectId(studentId: Long, subjectId: Long): List<DMAttendance> {
        return attendanceRepository.findByStudentIdAndSubjectId(studentId, subjectId)
    }
    
    fun findByStudentId(studentId: Long): List<DMAttendance> {
        return attendanceRepository.findByStudentId(studentId)
    }
    
    fun findBySubjectId(subjectId: Long): List<DMAttendance> {
        return attendanceRepository.findBySubjectId(subjectId)
    }
    
    fun findByStudentIdAndSubjectIdAndLectureDate(
        studentId: Long,
        subjectId: Long,
        lectureDate: java.time.LocalDate
    ): DMAttendance? {
        return attendanceRepository.findByStudentIdAndSubjectIdAndLectureDate(studentId, subjectId, lectureDate)
    }
    
    fun findByStudentIdAndSubjectIdAndLectureDateAfter(
        studentId: Long,
        subjectId: Long,
        lectureDate: java.time.LocalDate
    ): List<DMAttendance> {
        return attendanceRepository.findByStudentIdAndSubjectIdAndLectureDateAfter(studentId, subjectId, lectureDate)
    }
}

