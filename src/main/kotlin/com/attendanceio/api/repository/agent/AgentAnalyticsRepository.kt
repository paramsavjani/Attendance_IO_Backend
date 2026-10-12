package com.attendanceio.api.repository.agent

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * One student's standing in one subject, with the two figures kept apart: what the institute
 * published up to [cutoffDate], and what the student has marked in the app since. Combining them
 * is the caller's job so both columns can be shown side by side.
 */
data class SubjectAttendanceStanding(
    val semesterId: Long,
    val semesterYear: Int,
    val semesterType: String,
    val subjectCode: String,
    val subjectName: String,
    val officialPresent: Int,
    val officialTotal: Int,
    val cutoffDate: java.time.LocalDate?,
    val presentAfter: Int,
    val absentAfter: Int,
    val cancelledAfter: Int,
    val totalAfter: Int
) {
    val hasOfficial: Boolean get() = officialTotal > 0
    val combinedPresent: Int get() = officialPresent + presentAfter
    /** Cancelled classes are not classes, so they leave the denominator alone. */
    val combinedTotal: Int get() = (officialTotal + totalAfter - cancelledAfter).coerceAtLeast(0)
    val hasAnything: Boolean get() = combinedTotal > 0
}

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

    /**
     * Per-subject standing for one student across every semester: the institute's latest published
     * figures and the classes the student marked *after* that cutoff, returned separately.
     *
     * This is the same arithmetic as the `student_attendance_analytics` view, at subject
     * granularity instead of rolled up to the semester, so a per-subject answer here and the
     * overall percentage there can never disagree. A subject with no official row still comes back
     * (official columns zero, cutoff null) with every marked class counted, which is what the
     * student's own app view shows.
     *
     * The subject list is the union of the student's enrollments, the subjects the institute
     * published figures for, and the subjects they have marked classes in. Enrollments alone are
     * not enough: an official row whose `student_subject` row is missing would otherwise vanish
     * from the answer even though the institute published it.
     */
    fun subjectStandings(studentId: Long): List<SubjectAttendanceStanding> {
        val sql = """
            WITH latest_official AS (
                SELECT DISTINCT ON (ia.student_id, ia.subject_id)
                       ia.student_id, ia.subject_id, ia.present_classes, ia.total_classes, ia.cutoff_date
                FROM institute_attendance ia
                WHERE ia.student_id = :studentId AND ia.is_official = true
                ORDER BY ia.student_id, ia.subject_id, ia.cutoff_date DESC
            ),
            after_cutoff AS (
                SELECT a.subject_id,
                       COUNT(*) FILTER (WHERE a.status = 'PRESENT'::attendance_status_enum)   AS present_after,
                       COUNT(*) FILTER (WHERE a.status = 'ABSENT'::attendance_status_enum)    AS absent_after,
                       COUNT(*) FILTER (WHERE a.status = 'CANCELLED'::attendance_status_enum) AS cancelled_after,
                       COUNT(*)                                                               AS total_after
                FROM attendance a
                LEFT JOIN latest_official lo
                       ON lo.student_id = a.student_id AND lo.subject_id = a.subject_id
                WHERE a.student_id = :studentId
                  AND (lo.cutoff_date IS NULL OR a.lecture_date > lo.cutoff_date)
                  AND a.lecture_date <= CURRENT_DATE
                GROUP BY a.subject_id
            ),
            subject_ids AS (
                SELECT ss.subject_id FROM student_subject ss WHERE ss.student_id = :studentId
                UNION
                SELECT lo.subject_id FROM latest_official lo
                UNION
                SELECT ac.subject_id FROM after_cutoff ac
            )
            SELECT sub.semester_id, sem.year, sem.type, sub.code, sub.name,
                   COALESCE(lo.present_classes, 0), COALESCE(lo.total_classes, 0), lo.cutoff_date,
                   COALESCE(ac.present_after, 0), COALESCE(ac.absent_after, 0),
                   COALESCE(ac.cancelled_after, 0), COALESCE(ac.total_after, 0)
            FROM subject_ids si
            JOIN subjects sub ON sub.id = si.subject_id
            JOIN semesters sem ON sem.id = sub.semester_id
            LEFT JOIN latest_official lo ON lo.subject_id = si.subject_id
            LEFT JOIN after_cutoff ac ON ac.subject_id = si.subject_id
            ORDER BY sub.semester_id DESC, sub.code
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        return (entityManager.createNativeQuery(sql)
            .setParameter("studentId", studentId)
            .resultList as List<Array<*>>).map { row ->
            SubjectAttendanceStanding(
                semesterId = (row[0] as Number).toLong(),
                semesterYear = (row[1] as Number).toInt(),
                semesterType = row[2].toString(),
                subjectCode = row[3] as String,
                subjectName = row[4] as String,
                officialPresent = (row[5] as Number).toInt(),
                officialTotal = (row[6] as Number).toInt(),
                cutoffDate = when (val v = row[7]) {
                    is java.time.LocalDate -> v
                    is java.sql.Date -> v.toLocalDate()
                    else -> null
                },
                presentAfter = (row[8] as Number).toInt(),
                absentAfter = (row[9] as Number).toInt(),
                cancelledAfter = (row[10] as Number).toInt(),
                totalAfter = (row[11] as Number).toInt()
            )
        }
    }
}
