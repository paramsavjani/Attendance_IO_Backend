package com.underworldnetwork.api.repository.attendance

import com.underworldnetwork.api.model.attendance.DMInstituteAttendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface InstituteAttendanceRepository : JpaRepository<DMInstituteAttendance, Long> {
    fun findByStudentIdAndSubjectId(
        studentId: Long,
        subjectId: Long
    ): List<DMInstituteAttendance>

    fun findBySubjectId(subjectId: Long): List<DMInstituteAttendance>

    fun findByStudentId(studentId: Long): List<DMInstituteAttendance>

}
