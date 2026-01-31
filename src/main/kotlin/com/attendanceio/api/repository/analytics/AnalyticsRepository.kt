package com.attendanceio.api.repository.analytics

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class AnalyticsRepository(
    @PersistenceContext private val entityManager: EntityManager
) {
    
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
     */
    fun getAppStats(): AppStatsResult {
        // Total logged-in users (google_id not null)
        val totalUsersQuery = entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM student WHERE google_id IS NOT NULL
        """)
        val totalUsers = (totalUsersQuery.singleResult as? Number)?.toInt() ?: 0

        // Total attendance entries (from attendance table, logged-in users only)
        val totalAttendanceQuery = entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
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

        // Last 15 days attendance (by day)
        val attendanceLast15Days = getLast15DaysAttendance()
        // Last 15 days app_open events (by day)
        val appOpensLast15Days = getLast15DaysAppOpens()

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
            attendanceLast15Days = attendanceLast15Days,
            appOpensLast15Days = appOpensLast15Days
        )
    }

    private fun getLast15DaysAttendance(): List<Pair<String, Int>> {
        val query = entityManager.createNativeQuery("""
            SELECT DATE(a.created_at) as d, COUNT(*) as cnt
            FROM attendance a
            INNER JOIN student s ON a.student_id = s.id
            WHERE s.google_id IS NOT NULL
            AND a.created_at >= CURRENT_DATE - INTERVAL '14 days'
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
        return fillLast15Days(map)
    }

    private fun getLast15DaysAppOpens(): List<Pair<String, Int>> {
        val query = entityManager.createNativeQuery("""
            SELECT DATE(created_at) as d, COUNT(*) as cnt
            FROM user_event
            WHERE event_type = 'app_open'
            AND created_at >= CURRENT_DATE - INTERVAL '14 days'
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
        return fillLast15Days(map)
    }

    private fun fillLast15Days(dateToCount: Map<String, Int>): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        for (i in 14 downTo 0) {
            val date = java.time.LocalDate.now().minusDays(i.toLong())
            val dateStr = date.format(formatter)
            result.add(dateStr to (dateToCount[dateStr] ?: 0))
        }
        return result
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
    val attendanceLast15Days: List<Pair<String, Int>>,
    val appOpensLast15Days: List<Pair<String, Int>>
)

