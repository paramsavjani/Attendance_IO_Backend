package com.attendanceio.api.repository.agent

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/** One student's marked classes in one subject, from app data. */
data class SubjectStudentCounts(
    val studentId: Long,
    val name: String,
    val rollNumber: String,
    val present: Int,
    val absent: Int
)

/**
 * Aggregations the chat agent asks for that no existing endpoint computes: attendance of an
 * arbitrary *group* of students (a batch, a programme, everyone in a subject). Reads the same
 * `student_attendance_analytics` view the Analytics page uses, so a group average here and the
 * overall average there agree.
 */
@Repository
class AgentAnalyticsRepository(
    @PersistenceContext private val entityManager: EntityManager
) {
    /** Per-student semester percentage for students whose roll number starts with [sidPrefix] (null = everyone). */
    fun semesterPercentagesForGroup(semesterId: Long, sidPrefix: String?): List<Double> {
        val sql = """
            SELECT saa.attendance_percentage
            FROM student_attendance_analytics saa
            JOIN student s ON s.id = saa.student_id
            WHERE saa.semester_id = :semesterId
              AND (CAST(:sidPrefix AS TEXT) IS NULL OR s.sid LIKE CONCAT(CAST(:sidPrefix AS TEXT), '%'))
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        return (entityManager.createNativeQuery(sql)
            .setParameter("semesterId", semesterId)
            .setParameter("sidPrefix", sidPrefix)
            .resultList as List<Number>).map { it.toDouble() }
    }

    /** How many students have a roll number starting with [sidPrefix] and at least one subject in [semesterId]. */
    fun countStudentsInGroup(semesterId: Long, sidPrefix: String?): Int {
        val sql = """
            SELECT COUNT(DISTINCT s.id)
            FROM student s
            JOIN student_subject ss ON ss.student_id = s.id
            JOIN subjects sub ON sub.id = ss.subject_id
            WHERE sub.semester_id = :semesterId
              AND (CAST(:sidPrefix AS TEXT) IS NULL OR s.sid LIKE CONCAT(CAST(:sidPrefix AS TEXT), '%'))
        """.trimIndent()
        return (entityManager.createNativeQuery(sql)
            .setParameter("semesterId", semesterId)
            .setParameter("sidPrefix", sidPrefix)
            .singleResult as Number).toInt()
    }

    /**
     * Every student enrolled in [subjectId] with their marked present/absent counts (cancelled
     * classes and rows excluded from analytics are not counted). Students with nothing marked
     * come back with zeros so the caller can report "enrolled but no data".
     */
    fun subjectStudentCounts(subjectId: Long, sidPrefix: String?): List<SubjectStudentCounts> {
        val sql = """
            SELECT s.id, COALESCE(s.name, ''), s.sid,
                   COUNT(a.id) FILTER (WHERE a.status = 'PRESENT') AS present,
                   COUNT(a.id) FILTER (WHERE a.status = 'ABSENT') AS absent
            FROM student_subject ss
            JOIN student s ON s.id = ss.student_id
            LEFT JOIN attendance a
                   ON a.student_id = s.id AND a.subject_id = ss.subject_id
                  AND a.lecture_date <= CURRENT_DATE
                  AND (a.exclude_from_analytics IS NOT TRUE)
            WHERE ss.subject_id = :subjectId
              AND (CAST(:sidPrefix AS TEXT) IS NULL OR s.sid LIKE CONCAT(CAST(:sidPrefix AS TEXT), '%'))
            GROUP BY s.id, s.name, s.sid
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        return (entityManager.createNativeQuery(sql)
            .setParameter("subjectId", subjectId)
            .setParameter("sidPrefix", sidPrefix)
            .resultList as List<Array<*>>).map { row ->
            SubjectStudentCounts(
                studentId = (row[0] as Number).toLong(),
                name = row[1] as String,
                rollNumber = row[2] as String,
                present = (row[3] as Number).toInt(),
                absent = (row[4] as Number).toInt()
            )
        }
    }
}
