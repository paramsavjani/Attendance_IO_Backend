package com.attendanceio.api.repository.analytics

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class AnalyticsRepository(
    @PersistenceContext private val entityManager: EntityManager
) {

    companion object {
        /** Start date for app analytics "all time" (day charts and attendance-by-hour). */
        private val APP_ANALYTICS_START_DATE = LocalDate.of(2026, 1, 5)
    }
    
    /**
     * Get attendance statistics for all students in a semester
     * Uses the pre-calculated view for fast queries
     */
    fun getAnalyticsStats(semesterId: Long?): Array<Any> {
        val query = if (semesterId == null) {
            entityManager.createNativeQuery("""
                SELECT 
                    COUNT(DISTINCT student_id) AS total_students,
                    COUNT(DISTINCT semester_id) AS total_semesters,
                    AVG(attendance_percentage) AS avg_attendance,
                    COUNT(*) FILTER (WHERE attendance_percentage >= 70) AS above_70,
                    COUNT(*) FILTER (WHERE attendance_percentage < 60) AS below_60
                FROM student_attendance_analytics
            """)
        } else {
            entityManager.createNativeQuery("""
                SELECT 
                    COUNT(DISTINCT student_id) AS total_students,
                    COUNT(DISTINCT semester_id) AS total_semesters,
                    AVG(attendance_percentage) AS avg_attendance,
                    COUNT(*) FILTER (WHERE attendance_percentage >= 70) AS above_70,
                    COUNT(*) FILTER (WHERE attendance_percentage < 60) AS below_60
                FROM student_attendance_analytics
                WHERE semester_id = :semesterId
            """).setParameter("semesterId", semesterId)
        }
        
        val result = query.singleResult as Array<*>
        return arrayOf(
            result[0] ?: 0,
            result[1] ?: 0,
            result[2] ?: 0.0,
            result[3] ?: 0,
            result[4] ?: 0
        )
    }
    
    /**
     * Get attendance percentages for all students (for distribution and ranges)
     */
    fun getAttendancePercentages(semesterId: Long?): List<Double> {
        val query = if (semesterId == null) {
            entityManager.createNativeQuery("""
                SELECT attendance_percentage
                FROM student_attendance_analytics
                ORDER BY attendance_percentage
            """)
        } else {
            entityManager.createNativeQuery("""
                SELECT attendance_percentage
                FROM student_attendance_analytics
                WHERE semester_id = :semesterId
                ORDER BY attendance_percentage
            """).setParameter("semesterId", semesterId)
        }
        
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Number>).map { it.toDouble() }
    }
    
    /**
     * Get total subjects count for a semester
     */
    fun getTotalSubjects(semesterId: Long?): Int {
        val query = if (semesterId == null) {
            entityManager.createNativeQuery("SELECT COUNT(DISTINCT id) FROM subjects")
        } else {
            entityManager.createNativeQuery("SELECT COUNT(DISTINCT id) FROM subjects WHERE semester_id = :semesterId")
                .setParameter("semesterId", semesterId)
        }
        
        return (query.singleResult as Number).toInt()
    }

    /**
     * Get app-level analytics for the main app (non-admin) dashboard.
     * Returns aggregate stats: users, attendance, events, etc.
     * @param allTime if true, daily series are from [APP_ANALYTICS_START_DATE] to today; otherwise last 30 days.
     *               Attendance-by-hour is always all-time (from APP_ANALYTICS_START_DATE).
     */
    fun getAppStats(allTime: Boolean = false): AppStatsResult {
        // Total logged-in users (google_id not null)
        val totalUsersQuery = entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM student WHERE google_id IS NOT NULL
        """)
        val totalUsers = (totalUsersQuery.singleResult as? Number)?.toInt() ?: 0

        // Total attendance entries (from attendance table, logged-in users only; exclude bulk holiday rows)
        val totalAttendanceQuery = entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
            AND (a.exclude_from_analytics IS NOT TRUE)
        """)
        val totalAttendance = (totalAttendanceQuery.singleResult as? Number)?.toInt() ?: 0

        // Total events
        val totalEventsQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM user_event")
        val totalEvents = (totalEventsQuery.singleResult as? Number)?.toInt() ?: 0

        // Recent logins / app opens (last 7 days)
        val recentLoginsQuery = entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM user_event
            WHERE event_type IN ('app_open', 'login', 'page_view')
            AND created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
        """)
        val recentLogins = (recentLoginsQuery.singleResult as? Number)?.toInt() ?: 0

        // Events by type
        val eventsByTypeQuery = entityManager.createNativeQuery("""
            SELECT event_type, COUNT(*) as cnt FROM user_event GROUP BY event_type ORDER BY cnt DESC
        """)
        @Suppress("UNCHECKED_CAST")
        val eventsByTypeRows = eventsByTypeQuery.resultList as List<Array<*>>
        val eventsByType = eventsByTypeRows.associate { (it[0] as String) to (it[1] as Number).toInt() }

        // Notifications: FCM enabled vs disabled (logged-in users with SID like 2023/2024/2025)
        val notifQuery = entityManager.createNativeQuery("""
            SELECT
                COUNT(*) FILTER (WHERE fcm_token IS NOT NULL) as enabled,
                COUNT(*) FILTER (WHERE fcm_token IS NULL) as disabled
            FROM student
            WHERE google_id IS NOT NULL
            AND (sid LIKE '20230%' OR sid LIKE '20240%' OR sid LIKE '20250%')
        """)
        val notifRow = notifQuery.singleResult as Array<*>
        val notificationsEnabled = (notifRow[0] as? Number)?.toInt() ?: 0
        val notificationsDisabled = (notifRow[1] as? Number)?.toInt() ?: 0

        // Enrollments
        val enrollQuery = entityManager.createNativeQuery("""
            SELECT
                COUNT(*) as total_enrollments,
                COUNT(DISTINCT student_id) as students_with_subjects,
                COUNT(DISTINCT subject_id) as unique_subjects
            FROM student_subject
        """)
        val enrollRow = enrollQuery.singleResult as Array<*>
        val totalEnrollments = (enrollRow[0] as? Number)?.toInt() ?: 0
        val studentsWithSubjects = (enrollRow[1] as? Number)?.toInt() ?: 0
        val uniqueSubjects = (enrollRow[2] as? Number)?.toInt() ?: 0

        // Daily series: last 30 days or all-time from APP_ANALYTICS_START_DATE
        val attendanceLast30Days = if (allTime) getAllTimeAttendance() else getLast30DaysAttendance()
        val appOpensLast30Days = if (allTime) getAllTimeAppOpens() else getLast30DaysAppOpens()
        // Attendance by hour of day (0–23), always all-time from 5 Jan 2026
        val attendanceByHour = getAttendanceByHourAllTime()

        return AppStatsResult(
            totalUsers = totalUsers,
            totalAttendance = totalAttendance,
            totalEvents = totalEvents,
            recentLogins = recentLogins,
            eventsByType = eventsByType,
            notificationsEnabled = notificationsEnabled,
            notificationsDisabled = notificationsDisabled,
            totalEnrollments = totalEnrollments,
            studentsWithSubjects = studentsWithSubjects,
            uniqueSubjects = uniqueSubjects,
            attendanceLast30Days = attendanceLast30Days,
            appOpensLast30Days = appOpensLast30Days,
            attendanceByHour = attendanceByHour
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
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<*>>
        val map: Map<String, Int> = rows.associate { row ->
            Pair(
                row[0]?.toString()?.take(10) ?: "",
                (row[1] as? Number)?.toInt() ?: 0
            )
        }
        return fillLastNDays(30, map)
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
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<*>>
        val map: Map<String, Int> = rows.associate { row ->
            Pair(
                row[0]?.toString()?.take(10) ?: "",
                (row[1] as? Number)?.toInt() ?: 0
            )
        }
        return fillLastNDays(30, map)
    }

    private fun fillLastNDays(n: Int, dateToCount: Map<String, Int>): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        for (i in (n - 1) downTo 0) {
            val date = java.time.LocalDate.now().minusDays(i.toLong())
            val dateStr = date.format(formatter)
            result.add(dateStr to (dateToCount[dateStr] ?: 0))
        }
        return result
    }

    private fun getAllTimeAttendance(): List<Pair<String, Int>> {
        val startStr = APP_ANALYTICS_START_DATE.toString()
        val query = entityManager.createNativeQuery("""
            SELECT DATE(a.created_at) as d, COUNT(*) as cnt
            FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
            AND (a.exclude_from_analytics IS NOT TRUE)
            AND a.created_at >= CAST(:startDate AS timestamp)
            GROUP BY DATE(a.created_at)
            ORDER BY d ASC
        """).setParameter("startDate", startStr)
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<*>>
        val map: Map<String, Int> = rows.associate { row ->
            Pair(
                row[0]?.toString()?.take(10) ?: "",
                (row[1] as? Number)?.toInt() ?: 0
            )
        }
        return fillDaysFromStart(APP_ANALYTICS_START_DATE, LocalDate.now(), map)
    }

    private fun getAllTimeAppOpens(): List<Pair<String, Int>> {
        val startStr = APP_ANALYTICS_START_DATE.toString()
        val query = entityManager.createNativeQuery("""
            SELECT DATE(created_at) as d, COUNT(*) as cnt
            FROM user_event
            WHERE event_type = 'app_open'
            AND created_at >= CAST(:startDate AS timestamp)
            GROUP BY DATE(created_at)
            ORDER BY d ASC
        """).setParameter("startDate", startStr)
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<*>>
        val map: Map<String, Int> = rows.associate { row ->
            Pair(
                row[0]?.toString()?.take(10) ?: "",
                (row[1] as? Number)?.toInt() ?: 0
            )
        }
        return fillDaysFromStart(APP_ANALYTICS_START_DATE, LocalDate.now(), map)
    }

    private fun fillDaysFromStart(start: LocalDate, end: LocalDate, dateToCount: Map<String, Int>): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        var date = start
        while (!date.isAfter(end)) {
            val dateStr = date.format(formatter)
            result.add(dateStr to (dateToCount[dateStr] ?: 0))
            date = date.plusDays(1)
        }
        return result
    }

    /** Attendance count by hour of day (0–23), all-time from 5 Jan 2026. */
    private fun getAttendanceByHourAllTime(): List<Pair<Int, Int>> {
        val startStr = APP_ANALYTICS_START_DATE.toString()
        val query = entityManager.createNativeQuery("""
            SELECT EXTRACT(HOUR FROM a.created_at)::int as h, COUNT(*) as cnt
            FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
            AND (a.exclude_from_analytics IS NOT TRUE)
            AND a.created_at >= CAST(:startDate AS timestamp)
            GROUP BY EXTRACT(HOUR FROM a.created_at)
            ORDER BY h ASC
        """).setParameter("startDate", startStr)
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<*>>
        val map = rows.associate { row ->
            ((row[0] as? Number)?.toInt() ?: 0) to ((row[1] as? Number)?.toInt() ?: 0)
        }
        return (0..23).map { hour -> hour to (map[hour] ?: 0) }
    }
}

data class AppStatsResult(
    val totalUsers: Int,
    val totalAttendance: Int,
    val totalEvents: Int,
    val recentLogins: Int,
    val eventsByType: Map<String, Int>,
    val notificationsEnabled: Int,
    val notificationsDisabled: Int,
    val totalEnrollments: Int,
    val studentsWithSubjects: Int,
    val uniqueSubjects: Int,
    val attendanceLast30Days: List<Pair<String, Int>>,
    val appOpensLast30Days: List<Pair<String, Int>>,
    val attendanceByHour: List<Pair<Int, Int>>
)

