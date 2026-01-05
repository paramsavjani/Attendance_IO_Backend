package com.attendanceio.api.service

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Service to send attendance reminders after lectures end.
 * 
 * Logic:
 * - Lectures end at :50 (8:50, 9:50, 10:50, 11:50, 12:50)
 * - First check at 8:50 and 9:50 when lectures end
 * - Then every hour at :55 (9:55, 10:55, 11:55, etc.) check for any ended lectures
 * - For each student with FCM token:
 *   1. Get their timetable for today
 *   2. Check which lectures have ended (based on slot end time)
 *   3. Check if attendance has been marked for those lectures
 *   4. Send notification if not marked
 */
@Service
class AttendanceReminderService(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val fcmNotificationService: FcmNotificationService
) {
    private val logger = LoggerFactory.getLogger(AttendanceReminderService::class.java)

    /**
     * Scheduled task to check and send attendance reminders every hour at :55.
     * Runs at every hour at :55 IST.
     * This checks for any lectures that have ended and attendance hasn't been marked.
     * 
     * DISABLED: Attendance reminders are disabled. Only sleep reminders are active.
     */
    // @Scheduled(cron = "0 55 * * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersEveryHour() {
        // Disabled - attendance reminders are not being sent
        // Only sleep reminders are active
        return
        // val istZone = ZoneId.of("Asia/Kolkata")
        // val now = LocalDateTime.now(istZone)
        // val currentHour = now.hour
        // 
        // logger.info("Checking for attendance reminders at ${now} IST (hourly :55 check)")
        // checkAndSendAttendanceReminders(now)
    }

    /**
     * Main logic to check for ended lectures and send reminders if attendance is not marked.
     */
    private fun checkAndSendAttendanceReminders(now: LocalDateTime) {
        val istZone = ZoneId.of("Asia/Kolkata")
        val currentTime = now.toLocalTime()
        val today = now.toLocalDate()
        val dayOfWeek = today.dayOfWeek

        // Skip weekends
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

        // Get all students with FCM tokens
        val studentsWithFcmToken = studentRepositoryAppAction.findAllWithFcmToken()
        logger.info("Found ${studentsWithFcmToken.size} students with FCM tokens")

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

                // Find lectures that have ended (end time is before or equal to current time)
                // Handle both standard slots and custom times
                val endedLectures = timetableEntries.filter { entry ->
                    val endTime = if (entry.customEndTime != null) {
                        // Custom time entry
                        entry.customEndTime
                    } else {
                        // Standard slot entry
                        entry.slot?.endTime
                    }
                    // Check if lecture has ended (endTime <= currentTime)
                    endTime != null && !endTime.isAfter(currentTime)
                }

                if (endedLectures.isEmpty()) {
                    continue // No lectures have ended yet
                }

                // Check which ended lectures don't have attendance marked
                val unmarkedLectures = endedLectures.filter { entry ->
                    val subjectId = entry.subject?.id ?: return@filter false
                    val attendance = attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDate(
                        studentId,
                        subjectId,
                        today
                    )
                    attendance == null // No attendance record means not marked
                }

                if (unmarkedLectures.isEmpty()) {
                    continue // All ended lectures have attendance marked
                }

                // Send one notification per student listing all unmarked lectures
                // Group by subject to avoid duplicates (in case of multiple slots for same subject)
                val unmarkedSubjects = unmarkedLectures.mapNotNull { entry ->
                    entry.subject?.let { subject ->
                        // Get end time from custom time or standard slot
                        val endTime = if (entry.customEndTime != null) {
                            entry.customEndTime
                        } else {
                            entry.slot?.endTime
                        }
                        Pair(subject, endTime)
                    }
                }.distinctBy { it.first.id }

                // Send one notification with all unmarked subjects
                val success = sendAttendanceReminder(
                    student = student,
                    unmarkedSubjects = unmarkedSubjects,
                    date = today
                )

                if (success) {
                    remindersSent++
                    val subjectNames = unmarkedSubjects.joinToString(", ") { it.first.name ?: "lecture" }
                    logger.info(
                        "✅ Attendance reminder sent to student ${student.id} (${student.name}) " +
                        "for unmarked lectures: $subjectNames"
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
            "Attendance reminder check completed at ${now}. " +
            "Checked $studentsChecked students, sent $remindersSent reminders"
        )
    }

    /**
     * Send attendance reminder notification via FCM.
     * 
     * @param student The student to send notification to
     * @param unmarkedSubjects List of unmarked subjects with their end times
     * @param date Date of the lecture
     * @return true if sent successfully, false otherwise
     */
    private fun sendAttendanceReminder(
        student: DMStudent,
        unmarkedSubjects: List<Pair<DMSubject, LocalTime?>>,
        date: LocalDate
    ): Boolean {
        val fcmToken = student.fcmToken ?: return false

        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")

        val title = "📝 Mark Your Attendance!"
        
        // Build body message based on number of unmarked subjects
        val body = if (unmarkedSubjects.size == 1) {
            val (subject, endTime) = unmarkedSubjects.first()
            val subjectName = subject.name ?: "lecture"
            val subjectCode = subject.code ?: ""
            val endTimeStr = endTime?.format(timeFormatter) ?: "recently"
            "Your $subjectName ($subjectCode) lecture ended at $endTimeStr. " +
            "Don't forget to mark your attendance for ${date.format(dateFormatter)}!"
        } else {
            val subjectNames = unmarkedSubjects.joinToString(", ") { it.first.name ?: "lecture" }
            "You have ${unmarkedSubjects.size} unmarked lectures: $subjectNames. " +
            "Don't forget to mark your attendance for ${date.format(dateFormatter)}!"
        }

        // Build data payload with all unmarked subjects
        val data = mutableMapOf<String, String>(
            "type" to "attendance_reminder",
            "date" to date.toString(),
            "count" to unmarkedSubjects.size.toString()
        )
        
        unmarkedSubjects.forEachIndexed { index, (subject, endTime) ->
            data["subject${index}Name"] = subject.name ?: ""
            data["subject${index}Code"] = subject.code ?: ""
            data["subject${index}EndTime"] = endTime?.toString() ?: ""
        }

        return fcmNotificationService.sendNotification(
            fcmToken = fcmToken,
            title = title,
            body = body,
            data = data
        )
    }
}

