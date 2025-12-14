package com.underworldnetwork.api.repository.attendance

import com.underworldnetwork.api.model.attendance.AttendanceSource
import com.underworldnetwork.api.model.attendance.AttendanceStatus
import com.underworldnetwork.api.model.attendance.DMAttendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface AttendanceRepository : JpaRepository<DMAttendance, Long> {
    fun findByStudentIdAndSubjectIdAndLectureDate(
        studentId: Long,
        subjectId: Long,
        lectureDate: LocalDate
    ): DMAttendance?

    fun findByStudentIdAndSubjectId(
        studentId: Long,
        subjectId: Long
    ): List<DMAttendance>

    fun findBySubjectIdAndLectureDate(
        subjectId: Long,
        lectureDate: LocalDate
    ): List<DMAttendance>

    fun findByStudentId(studentId: Long): List<DMAttendance>

    fun findBySubjectId(subjectId: Long): List<DMAttendance>

    fun findByStatus(status: AttendanceStatus): List<DMAttendance>
    
    fun findBySourceId(sourceId: AttendanceSource): List<DMAttendance>
    
    fun findByStudentIdAndStatus(
        studentId: Long,
        status: AttendanceStatus
    ): List<DMAttendance>
}

