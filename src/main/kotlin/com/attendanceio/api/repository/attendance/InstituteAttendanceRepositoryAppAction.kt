package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.DMInstituteAttendance
import org.springframework.stereotype.Component

@Component
class InstituteAttendanceRepositoryAppAction(
    private val instituteAttendanceRepository: InstituteAttendanceRepository
) {
    fun findByStudentIdAndSubjectId(studentId: Long, subjectId: Long): List<DMInstituteAttendance> {
        return instituteAttendanceRepository.findByStudentIdAndSubjectId(studentId, subjectId)
    }
    
    fun findByStudentId(studentId: Long): List<DMInstituteAttendance> {
        return instituteAttendanceRepository.findByStudentId(studentId)
    }
    
}
