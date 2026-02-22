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

    @Query("SELECT s FROM DMStudent s WHERE s.fcmToken IS NOT NULL AND s.dailyReminderAt18 = true")
    fun findStudentsForDailyReminderAt18(): List<DMStudent>

    @Query("SELECT s FROM DMStudent s WHERE s.fcmToken IS NOT NULL AND s.dailyReminderAt20 = true")
    fun findStudentsForDailyReminderAt20(): List<DMStudent>

    @Query("SELECT s FROM DMStudent s WHERE s.fcmToken IS NOT NULL AND s.dailyReminderAt22 = true")
    fun findStudentsForDailyReminderAt22(): List<DMStudent>
}
