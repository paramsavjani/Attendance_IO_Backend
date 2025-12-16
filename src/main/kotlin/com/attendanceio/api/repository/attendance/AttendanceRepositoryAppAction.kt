package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.AttendanceCalculationResult
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
    
    fun calculateStudentAttendanceBySubject(studentId: Long): List<AttendanceCalculationResult> {
        val results = attendanceRepository.calculateStudentAttendanceBySubject(studentId)
        return results.map { row ->
            AttendanceCalculationResult(
                subjectId = (row[0] as Number).toLong(),
                subjectCode = row[1] as String,
                subjectName = row[2] as String,
                subjectColor = row[3] as? String ?: "#3B82F6",
                semesterId = (row[4] as Number).toLong(),
                semesterYear = (row[5] as Number).toInt(),
                semesterType = row[6] as String,
                basePresent = (row[7] as Number).toInt(),
                baseAbsent = (row[8] as Number).toInt(),
                baseTotal = (row[9] as Number).toInt(),
                presentAfterCutoff = (row[10] as Number).toInt(),
                absentAfterCutoff = (row[11] as Number).toInt(),
                leaveAfterCutoff = (row[12] as Number).toInt(),
                totalAfterCutoff = (row[13] as Number).toInt()
            )
        }
    }
    
    fun save(attendance: DMAttendance): DMAttendance {
        return attendanceRepository.save(attendance)
    }
    
    fun delete(attendance: DMAttendance) {
        attendanceRepository.delete(attendance)
    }
}

