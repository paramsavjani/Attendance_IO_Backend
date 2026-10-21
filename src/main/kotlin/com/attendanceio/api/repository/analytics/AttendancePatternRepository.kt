package com.attendanceio.api.repository.analytics

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/** Present/absent marks for one combination of batch, weekday, start hour and class kind. */
data class AttendancePatternRow(
    /** First four sid digits for B.Tech students, null for everyone else. */
    val batch: String?,
    /** ISO weekday, 1 = Monday. */
    val weekday: Int,
    /** Start hour of the class, null when the mark has no time attached. */
    val hour: Int?,
    /** LECTURE, LAB or EXTRA. */
    val kind: String,
    val present: Int,
    val absent: Int
)

/**
 * Patterns in the attendance students mark in the app during the active semester. Only
 * PRESENT/ABSENT marks count (cancelled classes and leave are not attendance), and rows
 * excluded from analytics or dated in the future are skipped, as everywhere else.
 */
@Repository
class AttendancePatternRepository(
    @PersistenceContext private val entityManager: EntityManager
) {
    fun patternRows(): List<AttendancePatternRow> {
        val sql = """
            WITH marks AS (
                SELECT
                    CASE WHEN st.sid ~ '^[0-9]{4}0[0-9]' THEN LEFT(st.sid, 4) END AS batch,
                    CAST(EXTRACT(ISODOW FROM a.lecture_date) AS INTEGER) AS weekday,
                    CAST(EXTRACT(HOUR FROM COALESCE(ts.start_time, a.custom_start_time)) AS INTEGER) AS hour,
                    CASE
                        WHEN a.is_extra_class THEN 'EXTRA'
                        WHEN EXISTS (
                            SELECT 1
                            FROM student_lab_timetable lt
                            JOIN week_days wd ON wd.id = lt.day_id
                            WHERE lt.student_id = a.student_id
                              AND lt.subject_id = a.subject_id
                              AND UPPER(wd.name) = TO_CHAR(a.lecture_date, 'FMDAY')
                              AND (lt.slot_id = a.time_slot_id
                                   OR (lt.slot_id IS NULL AND lt.custom_start_time = a.custom_start_time))
                        ) THEN 'LAB'
                        ELSE 'LECTURE'
                    END AS kind,
                    a.status
                FROM attendance a
                JOIN student st ON st.id = a.student_id
                JOIN subjects sub ON sub.id = a.subject_id
                LEFT JOIN time_slots ts ON ts.id = a.time_slot_id
                WHERE sub.semester_id IN (SELECT id FROM semesters WHERE is_active = true)
                  AND a.lecture_date <= CURRENT_DATE
                  AND a.exclude_from_analytics IS NOT TRUE
                  AND a.status IN ('PRESENT', 'ABSENT')
            )
            SELECT batch, weekday, hour, kind,
                   COUNT(*) FILTER (WHERE status = 'PRESENT') AS present,
                   COUNT(*) FILTER (WHERE status = 'ABSENT') AS absent
            FROM marks
            GROUP BY batch, weekday, hour, kind
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        return (entityManager.createNativeQuery(sql).resultList as List<Array<*>>).map { row ->
            AttendancePatternRow(
                batch = row[0] as String?,
                weekday = (row[1] as Number).toInt(),
                hour = (row[2] as Number?)?.toInt(),
                kind = row[3] as String,
                present = (row[4] as Number).toInt(),
                absent = (row[5] as Number).toInt()
            )
        }
    }

    /** Distinct students with at least one counted mark, per B.Tech batch (null = everyone else). */
    fun studentCountsByBatch(): Map<String?, Int> {
        val sql = """
            SELECT CASE WHEN st.sid ~ '^[0-9]{4}0[0-9]' THEN LEFT(st.sid, 4) END AS batch,
                   COUNT(DISTINCT a.student_id)
            FROM attendance a
            JOIN student st ON st.id = a.student_id
            JOIN subjects sub ON sub.id = a.subject_id
            WHERE sub.semester_id IN (SELECT id FROM semesters WHERE is_active = true)
              AND a.lecture_date <= CURRENT_DATE
              AND a.exclude_from_analytics IS NOT TRUE
              AND a.status IN ('PRESENT', 'ABSENT')
            GROUP BY 1
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        return (entityManager.createNativeQuery(sql).resultList as List<Array<*>>)
            .associate { row -> (row[0] as String?) to (row[1] as Number).toInt() }
    }
}
