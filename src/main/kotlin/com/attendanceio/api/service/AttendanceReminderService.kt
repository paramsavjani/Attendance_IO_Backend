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
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Service to send attendance reminders 5 minutes after each lecture ends.
 * 
 * Logic:
 * - Runs only at 8:55, 9:55, 10:55, 11:55, 12:55 IST on weekdays (Mon–Fri)
 * - At each run, finds lectures that ended at :50 that hour (8:50, 9:50, 10:50, 11:50, 12:50)
 * - For each student with FCM token: if any such lecture has no attendance marked, sends one reminder
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
     * Scheduled task to check and send attendance reminders at 8:55, 9:55, 10:55, 11:55, 12:55 IST.
     * Sends a reminder 5 minutes after lectures ending at 8:50, 9:50, 10:50, 11:50, 12:50. Weekdays only.
     */
    @Scheduled(cron = "0 55 8-12 * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersFiveMinutesAfterLecture() {
        val istZone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.now(istZone)
        logger.info("Checking for attendance reminders at ${now} IST (5 min after lecture end)")
        checkAndSendAttendanceReminders(now)
    }

    /**
     * Main logic: find lectures that ended exactly 5 minutes ago and send reminders if attendance is not marked.
     */
    private fun checkAndSendAttendanceReminders(now: LocalDateTime) {
        val istZone = ZoneId.of("Asia/Kolkata")
        val currentTime = now.toLocalTime()
        val today = now.toLocalDate()
        val dayOfWeek = today.dayOfWeek

        // Skip weekends (cron is MON-FRI but safety check)
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            logger.debug("Today is weekend. Skipping attendance reminders.")
            return
        }

        // Lecture end time that triggers this run = current time minus 5 minutes (truncate to minutes for comparison)
        val lectureEndTimeFiveMinutesAgo = currentTime.minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES)

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

        // Get students with FCM token who have "after lecture" reminder enabled
        val allWithFcm = studentRepositoryAppAction.findAllWithFcmToken()
        val studentsWithFcmToken = allWithFcm.filter { it.afterLectureReminderEnabled }
        logger.info("Found ${studentsWithFcmToken.size} students with FCM and after-lecture reminder enabled (of ${allWithFcm.size} with FCM)")

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

                // Find lectures that ended exactly 5 minutes ago (by slot or custom end time)
                val endedLectures = timetableEntries.filter { entry ->
                    val endTime = if (entry.customEndTime != null) {
                        entry.customEndTime
                    } else {
                        entry.slot?.endTime
                    }
                    val endTimeTruncated = endTime?.truncatedTo(ChronoUnit.MINUTES)
                    endTimeTruncated != null && endTimeTruncated == lectureEndTimeFiveMinutesAgo
                }

                if (endedLectures.isEmpty()) {
                    continue // No lectures have ended yet
                }

                // Check which ended lectures don't have attendance marked (by slot or custom time)
                val unmarkedLectures = endedLectures.filter { entry ->
                    val subjectId = entry.subject?.id ?: return@filter false
                    !hasAttendanceForEntry(studentId, subjectId, today, entry)
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
     * Check if attendance exists for a specific timetable entry (by slot or custom times).
     */
    private fun hasAttendanceForEntry(
        studentId: Long,
        subjectId: Long,
        date: LocalDate,
        timetableEntry: DMStudentTimetable
    ): Boolean {
        val bySlot = timetableEntry.slot != null && timetableEntry.slot!!.id != null
        val byCustom = timetableEntry.customStartTime != null && timetableEntry.customEndTime != null
        return when {
            bySlot -> {
                val slotId = timetableEntry.slot!!.id!!
                attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndTimeSlotId(
                    studentId, subjectId, date, slotId
                ) != null
            }
            byCustom -> {
                attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDateAndCustomStartTimeAndCustomEndTime(
                    studentId, subjectId, date,
                    timetableEntry.customStartTime!!,
                    timetableEntry.customEndTime!!
                ) != null
            }
            else -> {
                val general = attendanceRepositoryAppAction.findByStudentIdAndSubjectIdAndLectureDate(
                    studentId, subjectId, date
                )
                general?.let { it.timeSlot == null && it.customStartTime == null && it.customEndTime == null } ?: false
            }
        }
    }

    /**
     * Returns a fixed title and a random funny body for the 5-min-after-lecture reminder (that one subject only).
     */
    private fun funnyReminderForSubject(subjectName: String): Pair<String, String> {
        val title = "$subjectName is waiting 👀"
        val bodies = listOf(
            "One tap now = fewer problems later. Go on.",
            "Mark attendance. Your GPA might not notice, but your percentage will.",
            "Tiny action. Big relief. Tap and relax.",
            "You survived the lecture. Finish the mission.",
            "Attendance first, procrastination later.",
            "Your attendance is feeling ignored. One tap = instant forgiveness.",
            "That lecture ended… but your attendance is still waiting",
            "Your future self just whispered: \"Please mark attendance.\"",
            "Attendance check! This is your friendly (slightly judgy) reminder.",
            "Your percentage is fragile. Handle with one tap.",
        )
        val body = bodies[Random.nextInt(bodies.size)]
        return Pair(title, body)
    }

    /**
     * Send attendance reminder notification via FCM.
     * For the 5-min-after-lecture reminder we use a funny, subject-specific message (single subject only).
     *
     * @param student The student to send notification to
     * @param unmarkedSubjects List of unmarked subjects with their end times (typically one for 5-min-after)
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

        // 5-min-after reminder: one subject only, use funny custom message
        val (title, body) = if (unmarkedSubjects.size == 1) {
            val (subject, _) = unmarkedSubjects.first()
            val subjectName = subject.name ?: "Your lecture"
            funnyReminderForSubject(subjectName)
        } else {
            val subjectNames = unmarkedSubjects.joinToString(", ") { it.first.name ?: "lecture" }
            Pair(
                "📝 Mark your attendance!",
                "Quick reminder: $subjectNames — don't forget to mark for ${date.format(dateFormatter)}!"
            )
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

