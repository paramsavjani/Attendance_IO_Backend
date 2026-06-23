package com.attendanceio.api.service

import com.attendanceio.api.model.notification.DMNotificationSent
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.notification.NotificationSentRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sends daily attendance reminders at 6 PM, 8 PM, and 10 PM on weekdays.
 * Only notifies students who still have unmarked lectures for that day.
 */
@Service
class DailyAttendanceCheckService(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val fcmNotificationService: FcmNotificationService,
    private val notificationSentRepositoryAppAction: NotificationSentRepositoryAppAction,
    private val attendanceExistenceChecker: AttendanceExistenceChecker
) {
    private val logger = LoggerFactory.getLogger(DailyAttendanceCheckService::class.java)

    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersAt6PM() {
        val now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
        logger.info("Checking for attendance reminders at 6:00 PM IST — $now")
        checkAndSendAttendanceReminders(now, 18)
    }

    @Scheduled(cron = "0 0 20 * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersAt8PM() {
        val now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
        logger.info("Checking for attendance reminders at 8:00 PM IST — $now")
        checkAndSendAttendanceReminders(now, 20)
    }

    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersAt10PM() {
        val now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
        logger.info("Checking for attendance reminders at 10:00 PM IST — $now")
        checkAndSendAttendanceReminders(now, 22)
    }

    private fun checkAndSendAttendanceReminders(now: LocalDateTime, notificationTime: Int) {
        val today = now.toLocalDate()
        val dayOfWeek = today.dayOfWeek

        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            logger.debug("Today is weekend. Skipping attendance reminders.")
            return
        }

        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            logger.debug("No active semester found. Skipping attendance reminders.")
            return
        }
        val currentSemesterId = activeSemesters.first().id ?: return

        val todayDayName = dayOfWeek.name

        val studentsWithFcmToken = studentRepositoryAppAction.findStudentsForDailyReminderAtHour(notificationTime)
        logger.info("Found ${studentsWithFcmToken.size} students for daily reminder at $notificationTime:00 IST")

        var remindersSent = 0
        var studentsChecked = 0

        for (student in studentsWithFcmToken) {
            try {
                val studentId = student.id ?: continue
                studentsChecked++

                val timetableEntries = studentTimetableRepositoryAppAction
                    .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
                    .filter { it.day?.name?.uppercase() == todayDayName }

                if (timetableEntries.isEmpty()) continue

                val unmarkedLectures = timetableEntries.filter { entry ->
                    val subjectId = entry.subject?.id ?: return@filter false
                    !attendanceExistenceChecker.hasAttendance(studentId, subjectId, today, entry)
                }

                if (unmarkedLectures.isEmpty()) continue

                val unmarkedSubjects = unmarkedLectures.mapNotNull { it.subject }.distinctBy { it.id }
                val subjectIds = unmarkedSubjects.mapNotNull { it.id }

                val success = sendAttendanceReminder(student, unmarkedSubjects.size, today)

                if (success) {
                    remindersSent++
                    try {
                        notificationSentRepositoryAppAction.save(DMNotificationSent().apply {
                            this.student = student
                            this.notificationTime = notificationTime
                            this.notificationDate = today
                            this.subjectIds = subjectIds
                        })
                    } catch (e: Exception) {
                        logger.error("Failed to record notification for student ${student.id}: ${e.message}", e)
                    }
                    logger.info(
                        "✅ Attendance reminder sent to student ${student.id} (${student.name}) " +
                        "for ${unmarkedSubjects.size} unmarked lecture(s)"
                    )
                } else {
                    logger.warn("❌ Failed to send attendance reminder to student ${student.id} (${student.name})")
                }
            } catch (e: Exception) {
                logger.error("Error processing attendance reminder for student ${student.id}: ${e.message}", e)
            }
        }

        logger.info(
            "Daily attendance check completed at $now. " +
            "Checked $studentsChecked students, sent $remindersSent reminders"
        )
    }

    private fun sendAttendanceReminder(student: DMStudent, unmarkedCount: Int, date: LocalDate): Boolean {
        val fcmToken = student.fcmToken ?: return false
        return fcmNotificationService.sendNotification(
            fcmToken = fcmToken,
            title = "📝 Attendance Reminder",
            body = "Don't forget! Mark attendance for $unmarkedCount remaining lectures today.",
            data = mapOf(
                "type" to "daily_attendance_reminder",
                "date" to date.toString(),
                "count" to unmarkedCount.toString()
            )
        )
    }
}
