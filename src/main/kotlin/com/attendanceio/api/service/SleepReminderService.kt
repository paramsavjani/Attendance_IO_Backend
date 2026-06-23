package com.attendanceio.api.service

import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.attendance.DMAttendance
import com.attendanceio.api.model.student.DMStudent
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
 * Sends sleep reminders based on each student's first lecture the following morning.
 *
 * Runs every hour but only acts during:
 *   - Evening/night (17:00–23:59): checks tomorrow's first lecture
 *   - Early morning (00:00–07:59): checks today's first lecture
 *
 * If sleeping now (current time + sleep duration) would mean waking exactly at lecture time,
 * a notification is sent. Critical lectures (attendance below minimum) get an urgent variant.
 */
@Service
class SleepReminderService(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val fcmNotificationService: FcmNotificationService,
    private val criticalLectureDetectorService: CriticalLectureDetectorService
) {
    private val logger = LoggerFactory.getLogger(SleepReminderService::class.java)

    private fun getLectureStartTime(timetableEntry: DMStudentTimetable): LocalTime? =
        timetableEntry.customStartTime ?: timetableEntry.slot?.startTime

    private fun isLectureCancelled(
        timetableEntry: DMStudentTimetable,
        attendanceRecordsForTargetDate: List<DMAttendance>
    ): Boolean {
        val subjectId = timetableEntry.subject?.id ?: return false
        val slotId = timetableEntry.slot?.id
        val customStart = timetableEntry.customStartTime
        val customEnd = timetableEntry.customEndTime

        return attendanceRecordsForTargetDate
            .asSequence()
            .filter { it.status == AttendanceStatus.CANCELLED }
            .filter { !it.isExtraClass }
            .filter { it.subject?.id == subjectId }
            .any { attendance ->
                val isGeneric = attendance.timeSlot == null &&
                    attendance.customStartTime == null &&
                    attendance.customEndTime == null
                val isSlotMatch = slotId != null && attendance.timeSlot?.id == slotId
                val isCustomMatch = customStart != null && customEnd != null &&
                    attendance.customStartTime == customStart &&
                    attendance.customEndTime == customEnd

                isGeneric || isSlotMatch || isCustomMatch
            }
    }

    @Scheduled(cron = "0 0 * * * ?", zone = "Asia/Kolkata")
    fun checkAndSendSleepReminders() {
        val istZone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.now(istZone)
        val currentTime = now.toLocalTime()
        val currentHour = now.hour
        logger.info("Checking for sleep reminders at $now IST (hour: $currentHour)")

        if (currentHour in 8..16) {
            logger.debug("Sleep reminder check skipped — only runs after 17:00 or in early morning (0:00–7:59)")
            return
        }

        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            logger.debug("No active semester found. Skipping sleep reminders.")
            return
        }
        val currentSemesterId = activeSemesters.first().id ?: return

        val targetDate: LocalDate
        val targetDayOfWeek: DayOfWeek

        if (currentHour < 8) {
            targetDate = now.toLocalDate()
            targetDayOfWeek = targetDate.dayOfWeek
            logger.debug("Early morning check (${currentHour}:00) — checking today's lectures for $targetDate")
        } else {
            targetDate = now.toLocalDate().plusDays(1)
            targetDayOfWeek = targetDate.dayOfWeek
            logger.debug("Evening/night check (${currentHour}:00) — checking tomorrow's lectures for $targetDate")
        }

        if (targetDayOfWeek == DayOfWeek.SATURDAY || targetDayOfWeek == DayOfWeek.SUNDAY) {
            logger.debug("Target day ($targetDate) is weekend. Skipping sleep reminders.")
            return
        }

        val targetDayName = targetDayOfWeek.name

        val studentsWithFcmToken = studentRepositoryAppAction.findAllWithFcmToken()
        logger.info("Found ${studentsWithFcmToken.size} students with FCM tokens")

        var remindersSent = 0
        var studentsChecked = 0

        for (student in studentsWithFcmToken) {
            try {
                val studentId = student.id ?: continue
                studentsChecked++

                val timetableEntries = studentTimetableRepositoryAppAction
                    .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
                    .filter { it.day?.name?.uppercase() == targetDayName }

                if (timetableEntries.isEmpty()) continue

                val attendanceRecordsForTargetDate = attendanceRepositoryAppAction
                    .findByStudentIdAndLectureDate(studentId, targetDate)

                val activeTimetableEntries = timetableEntries.filterNot {
                    isLectureCancelled(it, attendanceRecordsForTargetDate)
                }

                if (activeTimetableEntries.isEmpty()) {
                    logger.debug("All lectures cancelled for studentId=$studentId on $targetDate, skipping")
                    continue
                }

                val firstLecture = activeTimetableEntries.minByOrNull {
                    getLectureStartTime(it) ?: LocalTime.MAX
                } ?: continue

                val firstLectureTime = getLectureStartTime(firstLecture) ?: continue
                val firstLectureSubjectName = firstLecture.subject?.name ?: "lecture"
                val firstLectureSubjectId = firstLecture.subject?.id ?: continue

                val firstCriticalLecture = activeTimetableEntries
                    .filter { entry ->
                        val sid = entry.subject?.id ?: return@filter false
                        criticalLectureDetectorService.isCriticalLecture(studentId, sid)
                    }
                    .minByOrNull { getLectureStartTime(it) ?: LocalTime.MAX }

                val firstCriticalLectureTime = firstCriticalLecture?.let { getLectureStartTime(it) }
                val firstCriticalLectureSubjectName = firstCriticalLecture?.subject?.name ?: "lecture"

                val isFirstLectureCritical = criticalLectureDetectorService.isCriticalLecture(
                    studentId, firstLectureSubjectId
                )

                val wakeTime = currentTime.plusHours(student.sleepDurationHours.toLong())
                val wakeTimeHour = wakeTime.hour

                if (wakeTimeHour == firstLectureTime.hour) {
                    val success = sendSleepReminder(
                        student = student,
                        currentTime = currentTime,
                        wakeTime = wakeTime,
                        firstLectureTime = firstLectureTime,
                        subjectName = firstLectureSubjectName,
                        isCritical = isFirstLectureCritical
                    )
                    if (success) {
                        remindersSent++
                        logger.info(
                            "✅ Sleep reminder sent to student ${student.id} (${student.name}) " +
                            "for first lecture at $firstLectureTime " +
                            "(${if (isFirstLectureCritical) "CRITICAL" else "normal"})"
                        )
                    } else {
                        logger.warn("❌ Failed to send sleep reminder to student ${student.id} for first lecture")
                    }
                }

                if (firstCriticalLectureTime != null && firstCriticalLectureTime != firstLectureTime) {
                    if (wakeTimeHour == firstCriticalLectureTime.hour) {
                        val success = sendSleepReminder(
                            student = student,
                            currentTime = currentTime,
                            wakeTime = wakeTime,
                            firstLectureTime = firstCriticalLectureTime,
                            subjectName = firstCriticalLectureSubjectName,
                            isCritical = true
                        )
                        if (success) {
                            remindersSent++
                            logger.info(
                                "✅ CRITICAL sleep reminder sent to student ${student.id} (${student.name}) " +
                                "for critical lecture at $firstCriticalLectureTime"
                            )
                        } else {
                            logger.warn(
                                "❌ Failed to send CRITICAL sleep reminder to student ${student.id}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing sleep reminder for student ${student.id}: ${e.message}", e)
            }
        }

        logger.info(
            "Sleep reminder check completed at $now. " +
            "Checked $studentsChecked students, sent $remindersSent reminders"
        )
    }

    private fun sendSleepReminder(
        student: DMStudent,
        currentTime: LocalTime,
        wakeTime: LocalTime,
        firstLectureTime: LocalTime,
        subjectName: String,
        isCritical: Boolean
    ): Boolean {
        val fcmToken = student.fcmToken ?: return false
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")

        val title = if (isCritical) "⚠️ CRITICAL: Sleep Now!" else "😴 Time to Sleep!"
        val body = if (isCritical) {
            "You have a CRITICAL lecture at ${firstLectureTime.format(timeFormatter)} ($subjectName). " +
            "Your attendance is BELOW the minimum requirement!"
        } else {
            "You have a lecture at ${firstLectureTime.format(timeFormatter)} ($subjectName). " +
            "Sleep now and be well-rested!"
        }

        return fcmNotificationService.sendNotification(
            fcmToken = fcmToken,
            title = title,
            body = body,
            data = mapOf(
                "type" to "sleep_reminder",
                "currentTime" to currentTime.toString(),
                "wakeTime" to wakeTime.toString(),
                "lectureTime" to firstLectureTime.toString(),
                "subjectName" to subjectName,
                "isCritical" to isCritical.toString(),
                "sleepDurationHours" to student.sleepDurationHours.toString()
            )
        )
    }
}
