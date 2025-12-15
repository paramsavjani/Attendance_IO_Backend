package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.AttendanceSource
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.DMAttendance
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

    fun findByStudentId(studentId: Long): List<DMAttendance>

    fun findBySubjectId(subjectId: Long): List<DMAttendance>

    fun findByStatus(status: AttendanceStatus): List<DMAttendance>

}

