package com.attendanceio.api.repository.notification

import com.attendanceio.api.model.notification.DMNotificationSent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface NotificationSentRepository : JpaRepository<DMNotificationSent, Long> {
    fun findByStudentId(studentId: Long): List<DMNotificationSent>
    fun findByNotificationTime(notificationTime: Int): List<DMNotificationSent>
    fun findByNotificationDate(notificationDate: LocalDate): List<DMNotificationSent>
    fun findByStudentIdAndNotificationDate(studentId: Long, notificationDate: LocalDate): List<DMNotificationSent>
    fun findByStudentIdAndNotificationTime(studentId: Long, notificationTime: Int): List<DMNotificationSent>
    fun findByNotificationDateBetween(startDate: LocalDate, endDate: LocalDate): List<DMNotificationSent>
}
