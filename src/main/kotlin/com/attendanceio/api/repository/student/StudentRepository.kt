package com.attendanceio.api.repository.student

import com.attendanceio.api.model.student.DMStudent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StudentRepository : JpaRepository<DMStudent, Long> {
    fun findByEmail(email: String): DMStudent?
    fun findBySid(sid: String): DMStudent?
    fun findByNameContainingIgnoreCase(name: String): List<DMStudent>
    fun findBySidContainingIgnoreCase(sid: String): List<DMStudent>
    fun findTop10ByNameContainingIgnoreCase(name: String): List<DMStudent>
    fun findTop10BySidContainingIgnoreCase(sid: String): List<DMStudent>
    fun findByFcmTokenIsNotNull(): List<DMStudent>

    @Query(
        value = """
            SELECT *
            FROM student s
            WHERE s.name IS NOT NULL
              AND (
                LOWER(TRIM(REGEXP_REPLACE(s.name, '\s+', ' ', 'g'))) LIKE LOWER(CONCAT('%', TRIM(REGEXP_REPLACE(:query, '\s+', ' ', 'g')), '%'))
                OR LOWER(REGEXP_REPLACE(s.name, '\s+', '', 'g')) LIKE LOWER(CONCAT('%', REGEXP_REPLACE(:query, '\s+', '', 'g'), '%'))
                OR EXISTS (
                    SELECT 1
                    FROM unnest(string_to_array(LOWER(TRIM(REGEXP_REPLACE(:query, '\s+', ' ', 'g'))), ' ')) AS token
                    WHERE token <> ''
                      AND LOWER(s.name) LIKE CONCAT('%', token, '%')
                )
              )
            ORDER BY
              CASE
                WHEN LOWER(TRIM(REGEXP_REPLACE(s.name, '\s+', ' ', 'g'))) = LOWER(TRIM(REGEXP_REPLACE(:query, '\s+', ' ', 'g'))) THEN 0
                WHEN LOWER(s.name) LIKE LOWER(CONCAT(TRIM(REGEXP_REPLACE(:query, '\s+', ' ', 'g')), '%')) THEN 1
                WHEN LOWER(s.name) LIKE LOWER(CONCAT('% ', TRIM(REGEXP_REPLACE(:query, '\s+', ' ', 'g')), '%')) THEN 2
                ELSE 3
              END,
              s.name ASC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun searchByNameFlexible(@Param("query") query: String, @Param("limit") limit: Int): List<DMStudent>

    @Query("SELECT s FROM DMStudent s WHERE s.fcmToken IS NOT NULL AND s.dailyReminderAt18 = true")
    fun findStudentsForDailyReminderAt18(): List<DMStudent>

    @Query("SELECT s FROM DMStudent s WHERE s.fcmToken IS NOT NULL AND s.dailyReminderAt20 = true")
    fun findStudentsForDailyReminderAt20(): List<DMStudent>

    @Query("SELECT s FROM DMStudent s WHERE s.fcmToken IS NOT NULL AND s.dailyReminderAt22 = true")
    fun findStudentsForDailyReminderAt22(): List<DMStudent>
}
