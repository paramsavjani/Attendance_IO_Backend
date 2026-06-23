package com.attendanceio.api.service

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Sends a push notification 5 minutes after each lecture ends if attendance is not yet marked.
 * Runs at 8:55, 9:55, 10:55, 11:55, 12:55 IST on weekdays (Mon–Fri).
 */
@Service
class AttendanceReminderService(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val fcmNotificationService: FcmNotificationService,
    private val attendanceExistenceChecker: AttendanceExistenceChecker
) {
    private val logger = LoggerFactory.getLogger(AttendanceReminderService::class.java)

    @Scheduled(cron = "0 55 8-12 * * MON-FRI", zone = "Asia/Kolkata")
    fun checkAndSendAttendanceRemindersFiveMinutesAfterLecture() {
        val now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
        logger.info("Checking for attendance reminders at $now IST (5 min after lecture end)")
        checkAndSendAttendanceReminders(now)
    }

    private fun checkAndSendAttendanceReminders(now: LocalDateTime) {
        val currentTime = now.toLocalTime()
        val today = now.toLocalDate()
        val dayOfWeek = today.dayOfWeek

        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            logger.debug("Today is weekend. Skipping attendance reminders.")
            return
        }

        val lectureEndTimeFiveMinutesAgo = currentTime.minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES)

        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            logger.debug("No active semester found. Skipping attendance reminders.")
            return
        }
        val currentSemesterId = activeSemesters.first().id ?: return

        val todayDayName = dayOfWeek.name

        val allWithFcm = studentRepositoryAppAction.findAllWithFcmToken()
        val studentsWithFcmToken = allWithFcm.filter { it.afterLectureReminderEnabled }
        logger.info(
            "Found ${studentsWithFcmToken.size} students with FCM and after-lecture reminder enabled " +
            "(of ${allWithFcm.size} with FCM)"
        )

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

                val endedLectures = timetableEntries.filter { entry ->
                    val endTime = (entry.customEndTime ?: entry.slot?.endTime)
                        ?.truncatedTo(ChronoUnit.MINUTES)
                    endTime != null && endTime == lectureEndTimeFiveMinutesAgo
                }

                if (endedLectures.isEmpty()) continue

                val unmarkedLectures = endedLectures.filter { entry ->
                    val subjectId = entry.subject?.id ?: return@filter false
                    !attendanceExistenceChecker.hasAttendance(studentId, subjectId, today, entry)
                }

                if (unmarkedLectures.isEmpty()) continue

                val unmarkedSubjects = unmarkedLectures.mapNotNull { entry ->
                    entry.subject?.let { subject ->
                        val endTime = entry.customEndTime ?: entry.slot?.endTime
                        Pair(subject, endTime)
                    }
                }.distinctBy { it.first.id }

                val success = sendAttendanceReminder(student, unmarkedSubjects, today)

                if (success) {
                    remindersSent++
                    val subjectNames = unmarkedSubjects.joinToString(", ") { it.first.name ?: "lecture" }
                    logger.info(
                        "✅ Attendance reminder sent to student ${student.id} (${student.name}) " +
                        "for unmarked lectures: $subjectNames"
                    )
                } else {
                    logger.warn("❌ Failed to send attendance reminder to student ${student.id} (${student.name})")
                }
            } catch (e: Exception) {
                logger.error("Error processing attendance reminder for student ${student.id}: ${e.message}", e)
            }
        }

        logger.info(
            "Attendance reminder check completed at $now. " +
            "Checked $studentsChecked students, sent $remindersSent reminders"
        )
    }

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
        return Pair(title, bodies[Random.nextInt(bodies.size)])
    }

    private fun sendAttendanceReminder(
        student: DMStudent,
        unmarkedSubjects: List<Pair<DMSubject, LocalTime?>>,
        date: LocalDate
    ): Boolean {
        val fcmToken = student.fcmToken ?: return false
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

        val (title, body) = if (unmarkedSubjects.size == 1) {
            val subjectName = unmarkedSubjects.first().first.name ?: "Your lecture"
            funnyReminderForSubject(subjectName)
        } else {
            val subjectNames = unmarkedSubjects.joinToString(", ") { it.first.name ?: "lecture" }
            Pair(
                "📝 Mark your attendance!",
                "Quick reminder: $subjectNames — don't forget to mark for ${date.format(dateFormatter)}!"
            )
        }

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

        return fcmNotificationService.sendNotification(fcmToken = fcmToken, title = title, body = body, data = data)
    }
}
