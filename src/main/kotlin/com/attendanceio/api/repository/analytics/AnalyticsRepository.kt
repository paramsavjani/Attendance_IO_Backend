package com.attendanceio.api.repository.analytics

import com.attendanceio.api.model.analytics.AppStatsResult
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class AnalyticsRepository(
    @PersistenceContext private val entityManager: EntityManager
) {

    companion object {
        private val APP_ANALYTICS_START_DATE = LocalDate.of(2026, 1, 5)
    }

    fun getAnalyticsStats(semesterId: Long?): Array<Any> {
        val result = buildQueryForSemester("""
            SELECT
                COUNT(DISTINCT student_id) AS total_students,
                COUNT(DISTINCT semester_id) AS total_semesters,
                AVG(attendance_percentage) AS avg_attendance,
                COUNT(*) FILTER (WHERE attendance_percentage >= 70) AS above_70,
                COUNT(*) FILTER (WHERE attendance_percentage < 60) AS below_60
            FROM student_attendance_analytics
        """, semesterId = semesterId).singleResult as Array<*>
        return arrayOf(result[0] ?: 0, result[1] ?: 0, result[2] ?: 0.0, result[3] ?: 0, result[4] ?: 0)
    }

    fun getAttendancePercentages(semesterId: Long?): List<Double> {
        val query = buildQueryForSemester(
            "SELECT attendance_percentage FROM student_attendance_analytics",
            suffix = " ORDER BY attendance_percentage",
            semesterId = semesterId
        )
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Number>).map { it.toDouble() }
    }

    fun getTotalSubjects(semesterId: Long?): Int =
        (buildQueryForSemester("SELECT COUNT(DISTINCT id) FROM subjects", semesterId = semesterId)
            .singleResult as Number).toInt()

    fun getAppStats(allTime: Boolean = false): AppStatsResult {
        val totalUsers = (entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM student WHERE google_id IS NOT NULL"
        ).singleResult as? Number)?.toInt() ?: 0

        val totalAttendance = (entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL AND (a.exclude_from_analytics IS NOT TRUE)
        """).singleResult as? Number)?.toInt() ?: 0

        val totalEvents = (entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM user_event"
        ).singleResult as? Number)?.toInt() ?: 0

        val recentLogins = (entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM user_event
            WHERE event_type IN ('app_open', 'login', 'page_view')
            AND created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
        """).singleResult as? Number)?.toInt() ?: 0

        @Suppress("UNCHECKED_CAST")
        val eventsByType = (entityManager.createNativeQuery(
            "SELECT event_type, COUNT(*) as cnt FROM user_event GROUP BY event_type ORDER BY cnt DESC"
        ).resultList as List<Array<*>>).associate { (it[0] as String) to (it[1] as Number).toInt() }

        val notifRow = entityManager.createNativeQuery("""
            SELECT
                COUNT(*) FILTER (WHERE fcm_token IS NOT NULL) as enabled,
                COUNT(*) FILTER (WHERE fcm_token IS NULL) as disabled
            FROM student
            WHERE google_id IS NOT NULL
            AND (sid LIKE '20230%' OR sid LIKE '20240%' OR sid LIKE '20250%')
        """).singleResult as Array<*>

        val enrollRow = entityManager.createNativeQuery("""
            SELECT
                COUNT(*) as total_enrollments,
                COUNT(DISTINCT student_id) as students_with_subjects,
                COUNT(DISTINCT subject_id) as unique_subjects
            FROM student_subject
        """).singleResult as Array<*>

        return AppStatsResult(
            totalUsers = totalUsers,
            totalAttendance = totalAttendance,
            totalEvents = totalEvents,
            recentLogins = recentLogins,
            eventsByType = eventsByType,
            notificationsEnabled = (notifRow[0] as? Number)?.toInt() ?: 0,
            notificationsDisabled = (notifRow[1] as? Number)?.toInt() ?: 0,
            totalEnrollments = (enrollRow[0] as? Number)?.toInt() ?: 0,
            studentsWithSubjects = (enrollRow[1] as? Number)?.toInt() ?: 0,
            uniqueSubjects = (enrollRow[2] as? Number)?.toInt() ?: 0,
            attendanceLast30Days = if (allTime) getAllTimeAttendance() else getLast30DaysAttendance(),
            appOpensLast30Days = if (allTime) getAllTimeAppOpens() else getLast30DaysAppOpens(),
            attendanceByHour = getAttendanceByHourAllTime(),
            attendanceByDayOfWeek = getAttendanceByDayOfWeekAllTime()
        )
    }

    private fun getLast30DaysAttendance(): List<Pair<String, Int>> {
        val query = entityManager.createNativeQuery("""
            SELECT DATE(a.created_at) as d, COUNT(*) as cnt
            FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
            AND (a.exclude_from_analytics IS NOT TRUE)
            AND a.created_at >= CURRENT_DATE - INTERVAL '29 days'
            GROUP BY DATE(a.created_at)
            ORDER BY d ASC
        """)
        return fillLastNDays(30, toDateCountMap(query))
    }

    private fun getLast30DaysAppOpens(): List<Pair<String, Int>> {
        val query = entityManager.createNativeQuery("""
            SELECT DATE(created_at) as d, COUNT(*) as cnt
            FROM user_event
            WHERE event_type = 'app_open'
            AND created_at >= CURRENT_DATE - INTERVAL '29 days'
            GROUP BY DATE(created_at)
            ORDER BY d ASC
        """)
        return fillLastNDays(30, toDateCountMap(query))
    }

    private fun getAllTimeAttendance(): List<Pair<String, Int>> {
        val query = entityManager.createNativeQuery("""
            SELECT DATE(a.created_at) as d, COUNT(*) as cnt
            FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
            AND (a.exclude_from_analytics IS NOT TRUE)
            AND a.created_at >= CAST(:startDate AS timestamp)
            GROUP BY DATE(a.created_at)
            ORDER BY d ASC
        """).setParameter("startDate", APP_ANALYTICS_START_DATE.toString())
        return fillDaysFromStart(APP_ANALYTICS_START_DATE, LocalDate.now(), toDateCountMap(query))
    }

    private fun getAllTimeAppOpens(): List<Pair<String, Int>> {
        val query = entityManager.createNativeQuery("""
            SELECT DATE(created_at) as d, COUNT(*) as cnt
            FROM user_event
            WHERE event_type = 'app_open'
            AND created_at >= CAST(:startDate AS timestamp)
            GROUP BY DATE(created_at)
            ORDER BY d ASC
        """).setParameter("startDate", APP_ANALYTICS_START_DATE.toString())
        return fillDaysFromStart(APP_ANALYTICS_START_DATE, LocalDate.now(), toDateCountMap(query))
    }

    private fun getAttendanceByHourAllTime(): List<Pair<Int, Int>> {
        val query = entityManager.createNativeQuery("""
            SELECT EXTRACT(HOUR FROM a.created_at)::int as h, COUNT(*) as cnt
            FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
            AND (a.exclude_from_analytics IS NOT TRUE)
            AND a.created_at >= CAST(:startDate AS timestamp)
            GROUP BY EXTRACT(HOUR FROM a.created_at)
            ORDER BY h ASC
        """).setParameter("startDate", APP_ANALYTICS_START_DATE.toString())
        @Suppress("UNCHECKED_CAST")
        val map = (query.resultList as List<Array<*>>).associate { row ->
            ((row[0] as? Number)?.toInt() ?: 0) to ((row[1] as? Number)?.toInt() ?: 0)
        }
        return (0..23).map { hour -> hour to (map[hour] ?: 0) }
    }

    private fun getAttendanceByDayOfWeekAllTime(): List<Pair<Int, Int>> {
        val query = entityManager.createNativeQuery("""
            SELECT EXTRACT(DOW FROM a.created_at)::int as d, COUNT(*) as cnt
            FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
            AND (a.exclude_from_analytics IS NOT TRUE)
            AND a.created_at >= CAST(:startDate AS timestamp)
            GROUP BY EXTRACT(DOW FROM a.created_at)
            ORDER BY d ASC
        """).setParameter("startDate", APP_ANALYTICS_START_DATE.toString())
        @Suppress("UNCHECKED_CAST")
        val map = (query.resultList as List<Array<*>>).associate { row ->
            ((row[0] as? Number)?.toInt() ?: 0) to ((row[1] as? Number)?.toInt() ?: 0)
        }
        return (0..6).map { dow -> dow to (map[dow] ?: 0) }
    }

    private fun fillLastNDays(n: Int, dateToCount: Map<String, Int>): List<Pair<String, Int>> {
        val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        return (n - 1 downTo 0).map { i ->
            val date = LocalDate.now().minusDays(i.toLong())
            val dateStr = date.format(formatter)
            dateStr to (dateToCount[dateStr] ?: 0)
        }
    }

    private fun fillDaysFromStart(start: LocalDate, end: LocalDate, dateToCount: Map<String, Int>): List<Pair<String, Int>> {
        val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        val result = mutableListOf<Pair<String, Int>>()
        var date = start
        while (!date.isAfter(end)) {
            val dateStr = date.format(formatter)
            result.add(dateStr to (dateToCount[dateStr] ?: 0))
            date = date.plusDays(1)
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun toDateCountMap(query: Query): Map<String, Int> =
        (query.resultList as List<Array<*>>).associate { row ->
            (row[0]?.toString()?.take(10) ?: "") to ((row[1] as? Number)?.toInt() ?: 0)
        }

    private fun buildQueryForSemester(baseSql: String, suffix: String = "", semesterId: Long?): Query =
        if (semesterId == null) {
            entityManager.createNativeQuery(baseSql.trimIndent() + suffix)
        } else {
            entityManager.createNativeQuery("${baseSql.trimIndent()} WHERE semester_id = :semesterId$suffix")
                .setParameter("semesterId", semesterId)
        }
}
