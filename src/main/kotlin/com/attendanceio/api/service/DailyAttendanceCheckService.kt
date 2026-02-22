package com.attendanceio.api.service

import com.attendanceio.api.model.notification.DMNotificationSent
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
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
 * Service to check and send daily attendance reminders at 6 PM, 8 PM, and 10 PM on weekdays.
 * 
 * Logic:
 * - Runs at 6:00 PM (18:00), 8:00 PM (20:00), and 10:00 PM (22:00) IST on Monday to Friday
 * - For each student with FCM token:
 *   1. Get their timetable for today
 *   2. Check which lectures don't have attendance marked
 *   3. Send notification with count of remaining lectures
 */
@Service
class DailyAttendanceCheckService(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val fcmNotificationService: FcmNotificationService,
    private val notificationSentRepositoryAppAction: NotificationSentRepositoryAppAction
) {
    private val logger = LoggerFactory.getLogger(DailyAttendanceCheckService::class.java)

    /**
     * Scheduled task to check and send attendance reminders at 6:00 PM IST.
     * Runs on Monday to Friday.
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersAt6PM() {
        val istZone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.now(istZone)
        logger.info("Checking for attendance reminders at 6:00 PM IST - ${now}")
        checkAndSendAttendanceReminders(now, 18)
    }

    /**
     * Scheduled task to check and send attendance reminders at 8:00 PM IST.
     * Runs on Monday to Friday.
     */
    @Scheduled(cron = "0 0 20 * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersAt8PM() {
        val istZone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.now(istZone)
        logger.info("Checking for attendance reminders at 8:00 PM IST - ${now}")
        checkAndSendAttendanceReminders(now, 20)
    }

    /**
     * Scheduled task to check and send attendance reminders at 10:00 PM IST.
     * Runs on Monday to Friday.
     */
    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersAt10PM() {
        val istZone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.now(istZone)
        logger.info("Checking for attendance reminders at 10:00 PM IST - ${now}")
        checkAndSendAttendanceReminders(now, 22)
    }

    /**
     * Main logic to check for lectures without attendance and send reminders.
     * @param now Current date and time
     * @param notificationTime The hour when notification is sent (18 for 6 PM, 20 for 8 PM, 22 for 10 PM)
     */
    private fun checkAndSendAttendanceReminders(now: LocalDateTime, notificationTime: Int) {
        val istZone = ZoneId.of("Asia/Kolkata")
        val today = now.toLocalDate()
        val dayOfWeek = today.dayOfWeek

        // Skip weekends (shouldn't happen due to cron, but safety check)
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            logger.debug("Today is weekend. Skipping attendance reminders.")
            return
        }

        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            logger.debug("No active semester found. Skipping attendance reminders.")
            return
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: return

        // Map day of week to day name (for matching with database)
        val todayDayName = dayOfWeek.name // e.g., "MONDAY", "TUESDAY", etc.

        // Get students who want daily reminder at this hour (18, 20, or 22)
        val studentsWithFcmToken = studentRepositoryAppAction.findStudentsForDailyReminderAtHour(notificationTime)
        logger.info("Found ${studentsWithFcmToken.size} students for daily reminder at $notificationTime:00 IST")

        var remindersSent = 0
        var studentsChecked = 0

        for (student in studentsWithFcmToken) {
            try {
                val studentId = student.id ?: continue
                studentsChecked++

                // Get student's timetable for today
                val timetableEntries = studentTimetableRepositoryAppAction
                    .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
                    .filter { it.day?.name?.uppercase() == todayDayName }

                if (timetableEntries.isEmpty()) {
                    continue // No lectures today for this student
                }

                // Check which lectures don't have attendance marked
                val unmarkedLectures = mutableListOf<DMStudentTimetable>()

                for (entry in timetableEntries) {
                    val subjectId = entry.subject?.id ?: continue
                    val hasAttendance = checkAttendanceExists(studentId, subjectId, today, entry)
                    
                    if (!hasAttendance) {
                        unmarkedLectures.add(entry)
                    }
                }

                if (unmarkedLectures.isEmpty()) {
                    continue // All lectures have attendance marked
                }

                // Group by subject to avoid duplicates (in case of multiple slots for same subject)
                val unmarkedSubjects = unmarkedLectures.mapNotNull { entry ->
                    entry.subject
                }.distinctBy { it.id }

                // Get subject IDs for tracking
                val subjectIds = unmarkedSubjects.mapNotNull { it.id }

                // Send notification with count of remaining lectures
                val success = sendAttendanceReminder(
                    student = student,
                    unmarkedCount = unmarkedSubjects.size,
                    date = today
                )

                if (success) {
                    remindersSent++
                    
                    // Record notification sent in database
                    try {
                        val notificationSent = DMNotificationSent().apply {
                            this.student = student
                            this.notificationTime = notificationTime
                            this.notificationDate = today
                            this.subjectIds = subjectIds
                        }
                        notificationSentRepositoryAppAction.save(notificationSent)
                        logger.debug(
                            "Recorded notification sent for student ${student.id} at ${notificationTime}:00 " +
                            "for subjects: ${subjectIds.joinToString()}"
                        )
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to record notification sent for student ${student.id}: ${e.message}",
                            e
                        )
                    }
                    
                    logger.info(
                        "✅ Attendance reminder sent to student ${student.id} (${student.name}) " +
                        "for ${unmarkedSubjects.size} unmarked lecture(s)"
                    )
                } else {
                    logger.warn(
                        "❌ Failed to send attendance reminder to student ${student.id} (${student.name})"
                    )
                }
            } catch (e: Exception) {
                logger.error("Error processing attendance reminder for student ${student.id}: ${e.message}", e)
            }
        }

        logger.info(
            "Daily attendance check completed at ${now}. " +
            "Checked $studentsChecked students, sent $remindersSent reminders"
        )
    }

    /**
     * Check if attendance exists for a specific lecture.
     * Handles both time-specific attendance (by slot or custom times) and general attendance.
     */
    private fun checkAttendanceExists(
        studentId: Long,
        subjectId: Long,
        date: LocalDate,
        timetableEntry: DMStudentTimetable
    ): Boolean {
        // First check for time-specific attendance
        val hasTimeSpecificAttendance = when {
            // Check by time slot
            timetableEntry.slot != null && timetableEntry.slot!!.id != null -> {
                val slotId = timetableEntry.slot!!.id!!
                val attendance = attendanceRepositoryAppAction
                    .findByStudentIdAndSubjectIdAndLectureDateAndTimeSlotId(
                        studentId, subjectId, date, slotId
                    )
                attendance != null
            }
            // Check by custom times
            timetableEntry.customStartTime != null && timetableEntry.customEndTime != null -> {
                val attendance = attendanceRepositoryAppAction
                    .findByStudentIdAndSubjectIdAndLectureDateAndCustomStartTimeAndCustomEndTime(
                        studentId, subjectId, date,
                        timetableEntry.customStartTime!!,
                        timetableEntry.customEndTime!!
                    )
                attendance != null
            }
            else -> false
        }

        if (hasTimeSpecificAttendance) {
            return true
        }

        // Fallback: Check for general attendance (backward compatibility)
        // This handles cases where attendance was marked without time information
        val generalAttendance = attendanceRepositoryAppAction
            .findByStudentIdAndSubjectIdAndLectureDate(studentId, subjectId, date)
        
        // Only consider it as marked if it's a general record (no time slot or custom times)
        return generalAttendance?.let { 
            it.timeSlot == null && it.customStartTime == null && it.customEndTime == null 
        } ?: false
    }

    /**
     * Send attendance reminder notification via FCM.
     * 
     * @param student The student to send notification to
     * @param unmarkedCount Number of lectures without attendance
     * @param date Date of the lectures
     * @return true if sent successfully, false otherwise
     */
    private fun sendAttendanceReminder(
        student: DMStudent,
        unmarkedCount: Int,
        date: LocalDate
    ): Boolean {
        val fcmToken = student.fcmToken ?: return false

        val title = "📝 Attendance Reminder"
        
        // Build body message with count
        val body =
        "Don’t forget! Mark attendance for $unmarkedCount remaining lectures today."
    

        // Build data payload
        val data = mapOf(
            "type" to "daily_attendance_reminder",
            "date" to date.toString(),
            "count" to unmarkedCount.toString()
        )

        return fcmNotificationService.sendNotification(
            fcmToken = fcmToken,
            title = title,
            body = body,
            data = data
        )
    }
}

