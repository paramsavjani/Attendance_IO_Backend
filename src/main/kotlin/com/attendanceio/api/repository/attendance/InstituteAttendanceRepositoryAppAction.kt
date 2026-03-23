package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.DMInstituteAttendance
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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

    fun findByStudentIdAndIsOfficial(studentId: Long, isOfficial: Boolean): List<DMInstituteAttendance> {
        return instituteAttendanceRepository.findByStudentIdAndIsOfficial(studentId, isOfficial)
    }

    fun findBySubjectIdAndIsOfficial(subjectId: Long, isOfficial: Boolean): List<DMInstituteAttendance> {
        return instituteAttendanceRepository.findBySubjectIdAndIsOfficial(subjectId, isOfficial)
    }

    fun findByStudentIdAndSubjectIdAndIsOfficial(
        studentId: Long,
        subjectId: Long,
        isOfficial: Boolean
    ): DMInstituteAttendance? {
        return instituteAttendanceRepository.findByStudentIdAndSubjectIdAndIsOfficial(studentId, subjectId, isOfficial)
    }

    fun findByIsOfficial(isOfficial: Boolean): List<DMInstituteAttendance> {
        return instituteAttendanceRepository.findByIsOfficial(isOfficial)
    }

    fun getSubjectAnalysisSummary(): List<Array<Any>> {
        return instituteAttendanceRepository.getSubjectAnalysisSummary()
    }

    fun getLatestOfficialCutoffDate(): LocalDate? {
        return instituteAttendanceRepository.getLatestOfficialCutoffDate()
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
