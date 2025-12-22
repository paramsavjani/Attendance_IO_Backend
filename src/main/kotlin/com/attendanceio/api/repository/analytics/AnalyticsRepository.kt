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
}

