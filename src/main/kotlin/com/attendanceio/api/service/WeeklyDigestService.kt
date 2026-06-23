package com.attendanceio.api.service

import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Sends a weekly attendance digest every Sunday at 7 PM IST.
 *
 * weekTotal  = timetable-scheduled lectures for Mon–Sun minus cancelled ones
 * weekPresent = PRESENT records for the same period (excluding extra classes)
 *
 * Message: "This week: 18/22 classes attended (81.8%)."
 */
@Service
class WeeklyDigestService(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val fcmNotificationService: FcmNotificationService
) {
    private val logger = LoggerFactory.getLogger(WeeklyDigestService::class.java)

    @Scheduled(cron = "0 0 19 * * SUN", zone = "Asia/Kolkata")
    fun sendWeeklyDigest() {
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = today

        logger.info("Sending weekly digest for $weekStart – $weekEnd")

        val activeSemester = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()
        if (activeSemester == null) {
            logger.warn("No active semester found. Skipping weekly digest.")
            return
        }
        val semesterId = activeSemester.id ?: return

        val studentsWithFcm = studentRepositoryAppAction.findAllWithFcmToken()
        logger.info("Found ${studentsWithFcm.size} students with FCM tokens")

        var sent = 0

        for (student in studentsWithFcm) {
            try {
                val studentId = student.id ?: continue

                // Attendance records this week (regular classes only)
                val weeklyRecords = attendanceRepositoryAppAction
                    .findByStudentIdAndLectureDateBetween(studentId, weekStart, weekEnd)
                    .filter { !it.isExtraClass }

                val weekPresent = weeklyRecords.count { it.status == AttendanceStatus.PRESENT }
                val weekCancelled = weeklyRecords.count { it.status == AttendanceStatus.CANCELLED }

                // Timetable-based total: count scheduled slots for each day this week
                val timetableEntries = studentTimetableRepositoryAppAction
                    .findByStudentIdAndSemesterIdWithDetails(studentId, semesterId)

                val timetableDays = timetableEntries.mapNotNull {
                    ClassCalculationService.parseDayOfWeek(it.day?.name)
                }

                var scheduledThisWeek = 0
                var date = weekStart
                while (!date.isAfter(weekEnd)) {
                    scheduledThisWeek += timetableDays.count { it == date.dayOfWeek }
                    date = date.plusDays(1)
                }

                val weekTotal = maxOf(0, scheduledThisWeek - weekCancelled)

                if (weekTotal == 0) {
                    logger.debug("No scheduled lectures for student $studentId this week, skipping.")
                    continue
                }

                val pct = String.format("%.1f", weekPresent.toDouble() / weekTotal * 100)
                val body = "This week: $weekPresent/$weekTotal classes attended ($pct%)."

                val success = fcmNotificationService.sendNotification(
                    fcmToken = student.fcmToken ?: continue,
                    title = "📊 Your Weekly Summary",
                    body = body,
                    data = mapOf("type" to "weekly_digest")
                )

                if (success) {
                    sent++
                    logger.info("✅ Weekly digest sent to ${student.name} (id=$studentId): $body")
                } else {
                    logger.warn("❌ Failed to send weekly digest to ${student.name} (id=$studentId)")
                }
            } catch (e: Exception) {
                logger.error("Error sending weekly digest to student ${student.id}: ${e.message}", e)
            }
        }

        logger.info("Weekly digest done. Sent $sent/${studentsWithFcm.size} notifications.")
    }
}
