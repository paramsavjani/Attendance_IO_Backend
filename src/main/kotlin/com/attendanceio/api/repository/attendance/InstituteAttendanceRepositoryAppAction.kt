package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.DMInstituteAttendance
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

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
    
    fun save(instituteAttendance: DMInstituteAttendance): DMInstituteAttendance {
        return instituteAttendanceRepository.save(instituteAttendance)
    }
    
    @Transactional
    fun deleteAll(records: List<DMInstituteAttendance>) {
        if (records.isNotEmpty()) {
            instituteAttendanceRepository.deleteAll(records)
        }
    }
    
    @Transactional
    fun deleteAllByStudentIdAndSubjectId(studentId: Long, subjectId: Long) {
        val records = instituteAttendanceRepository.findByStudentIdAndSubjectId(studentId, subjectId)
        if (records.isNotEmpty()) {
            instituteAttendanceRepository.deleteAll(records)
        }
    }
    
}
