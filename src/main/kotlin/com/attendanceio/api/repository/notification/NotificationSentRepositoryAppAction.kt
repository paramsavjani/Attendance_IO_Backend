package com.attendanceio.api.repository.notification

import com.attendanceio.api.model.notification.DMNotificationSent
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class NotificationSentRepositoryAppAction(
    private val notificationSentRepository: NotificationSentRepository
) {
    fun save(notificationSent: DMNotificationSent): DMNotificationSent {
        return notificationSentRepository.save(notificationSent)
    }

    fun findByStudentId(studentId: Long): List<DMNotificationSent> {
        return notificationSentRepository.findByStudentId(studentId)
    }

    fun findByNotificationTime(notificationTime: Int): List<DMNotificationSent> {
        return notificationSentRepository.findByNotificationTime(notificationTime)
    }

    fun findByNotificationDate(notificationDate: LocalDate): List<DMNotificationSent> {
        return notificationSentRepository.findByNotificationDate(notificationDate)
    }

    fun findByStudentIdAndNotificationDate(studentId: Long, notificationDate: LocalDate): List<DMNotificationSent> {
        return notificationSentRepository.findByStudentIdAndNotificationDate(studentId, notificationDate)
    }

    fun findByStudentIdAndNotificationTime(studentId: Long, notificationTime: Int): List<DMNotificationSent> {
        return notificationSentRepository.findByStudentIdAndNotificationTime(studentId, notificationTime)
    }

    fun findByNotificationDateBetween(startDate: LocalDate, endDate: LocalDate): List<DMNotificationSent> {
        return notificationSentRepository.findByNotificationDateBetween(startDate, endDate)
    }
}
