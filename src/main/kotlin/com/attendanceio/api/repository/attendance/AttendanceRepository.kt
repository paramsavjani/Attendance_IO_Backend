package com.attendanceio.api.repository.attendance

import com.attendanceio.api.model.attendance.AttendanceSource
import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.DMAttendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    fun findByStudentIdAndSubjectIdAndLectureDateAfter(
        studentId: Long,
        subjectId: Long,
        lectureDate: LocalDate
    ): List<DMAttendance>

    @Query("""
        WITH latest_institute_attendance AS (
    SELECT DISTINCT ON (student_id, subject_id)
        student_id,
        subject_id,
        present_classes,
        total_classes,
        cutoff_date
    FROM institute_attendance
    WHERE student_id = :studentId
    ORDER BY student_id, subject_id, cutoff_date DESC
),
attendance_after_cutoff AS (
    SELECT
        a.student_id,
        a.subject_id,
        COUNT(*) FILTER (WHERE a.status = 'PRESENT') AS present_after,
        COUNT(*) FILTER (WHERE a.status = 'ABSENT')  AS absent_after,
        COUNT(*) FILTER (WHERE a.status = 'LEAVE')   AS leave_after,
        COUNT(*) AS total_after
    FROM attendance a
    LEFT JOIN latest_institute_attendance ia
        ON ia.student_id = a.student_id
       AND ia.subject_id = a.subject_id
    WHERE a.student_id = :studentId
      AND (ia.cutoff_date IS NULL OR a.lecture_date > ia.cutoff_date)
    GROUP BY a.student_id, a.subject_id
)
SELECT
    ss.subject_id                AS subjectId,
    s.code                       AS subjectCode,
    s.name                       AS subjectName,
    sem.id                       AS semesterId,
    sem.year                     AS semesterYear,
    sem.type::text               AS semesterType,

    COALESCE(ia.present_classes, 0)                    AS basePresent,
    COALESCE(ia.total_classes - ia.present_classes, 0) AS baseAbsent,
    COALESCE(ia.total_classes, 0)                      AS baseTotal,

    COALESCE(ac.present_after, 0) AS presentAfterCutoff,
    COALESCE(ac.absent_after, 0)  AS absentAfterCutoff,
    COALESCE(ac.leave_after, 0)   AS leaveAfterCutoff,
    COALESCE(ac.total_after, 0)   AS totalAfterCutoff

FROM student_subject ss
JOIN subjects s ON s.id = ss.subject_id
JOIN semesters sem ON sem.id = s.semester_id

LEFT JOIN latest_institute_attendance ia
    ON ia.student_id = ss.student_id
   AND ia.subject_id = ss.subject_id

LEFT JOIN attendance_after_cutoff ac
    ON ac.student_id = ss.student_id
   AND ac.subject_id = ss.subject_id

WHERE ss.student_id = :studentId
ORDER BY sem.year DESC, sem.type DESC, s.code;
    """, nativeQuery = true)
    fun calculateStudentAttendanceBySubject(@Param("studentId") studentId: Long): List<Array<Any>>
}

