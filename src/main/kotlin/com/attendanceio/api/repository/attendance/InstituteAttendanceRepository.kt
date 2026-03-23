package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.DMInstituteAttendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InstituteAttendanceRepository : JpaRepository<DMInstituteAttendance, Long> {
    fun findByStudentIdAndSubjectId(
        studentId: Long,
        subjectId: Long
    ): List<DMInstituteAttendance>

    fun findByStudentId(studentId: Long): List<DMInstituteAttendance>

    fun findByStudentIdAndIsOfficial(studentId: Long, isOfficial: Boolean): List<DMInstituteAttendance>

    fun findBySubjectIdAndIsOfficial(subjectId: Long, isOfficial: Boolean): List<DMInstituteAttendance>

    fun findByStudentIdAndSubjectIdAndIsOfficial(
        studentId: Long,
        subjectId: Long,
        isOfficial: Boolean
    ): DMInstituteAttendance?

    fun findByIsOfficial(isOfficial: Boolean): List<DMInstituteAttendance>

    @Query("""
        SELECT
            s.id AS subjectId,
            s.code AS subjectCode,
            s.name AS subjectName,
            s.color AS subjectColor,
            sem.id AS semesterId,
            COUNT(ia.id) AS totalStudents,
            AVG(CASE WHEN ia.total_classes > 0 THEN ia.present_classes * 100.0 / ia.total_classes ELSE 0 END) AS avgPercentage,
            MAX(ia.cutoff_date) AS cutoffDate
        FROM institute_attendance ia
        JOIN subjects s ON s.id = ia.subject_id
        JOIN semesters sem ON sem.id = s.semester_id
        WHERE ia.is_official = true
        GROUP BY s.id, s.code, s.name, s.color, sem.id
        ORDER BY s.code
    """, nativeQuery = true)
    fun getSubjectAnalysisSummary(): List<Array<Any>>

    @Query("""
        SELECT
            MAX(ia.cutoff_date)
        FROM institute_attendance ia
        WHERE ia.is_official = true
    """, nativeQuery = true)
    fun getLatestOfficialCutoffDate(): java.time.LocalDate?

}
