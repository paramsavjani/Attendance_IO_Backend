package com.attendanceio.api.service

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Service to calculate and send sleep reminders based on lecture schedules.
 * 
 * Production Logic:
 * - Runs every hour (8:00, 9:00, 10:00, etc.)
 * - For each student with FCM token:
 *   1. Gets their first lecture time tomorrow
 *   2. Calculates: current time + sleep duration = wake time
 *   3. If wake time matches first lecture time, sends notification NOW
 *   4. If lecture is critical (attendance < minimum criteria), sends critical reminder
 * 
 * Critical Lecture Definition:
 * - A lecture is critical if the student's attendance percentage for that subject
 *   is below their set minimum criteria threshold
 */
@Service
class SleepReminderService(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val fcmNotificationService: FcmNotificationService
) {
    private val logger = LoggerFactory.getLogger(SleepReminderService::class.java)


    /**
     * Check if a lecture is critical (attendance below minimum criteria).
     */
    fun isCriticalLecture(studentId: Long, subjectId: Long): Boolean {
        val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            ?: return false
        
        val minimumCriteria = studentSubject.minimumCriteria ?: return false
        
        // Get attendance stats for this subject
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        val subjectStats = attendanceResults.find { it.subjectId == subjectId } ?: return false
        
        val totalPresent = subjectStats.basePresent + subjectStats.presentAfterCutoff
        val totalAbsent = subjectStats.baseAbsent + subjectStats.absentAfterCutoff
        val total = totalPresent + totalAbsent
        
        if (total == 0) return false
        
        val percentage = (totalPresent.toDouble() / total) * 100
        return percentage < minimumCriteria
    }


    /**
     * Scheduled task to check and send sleep reminders.
     * Runs every hour at :00 minutes (e.g., 8:00, 9:00, 10:00, etc.)
     * 
     * Logic:
     * - For each student: current time + sleep duration = wake time
     * - If wake time matches first lecture time tomorrow, send notification NOW
     * - If the lecture is critical (attendance < minimum criteria), send critical reminder
     */
    @Scheduled(cron = "0 0 * * * ?") // Every hour at minute 0
    fun checkAndSendSleepReminders() {
        val now = LocalDateTime.now()
        val currentTime = now.toLocalTime()
        val currentHour = now.hour
        logger.info("Checking for sleep reminders at ${now} (current hour: $currentHour)")
        
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            logger.debug("No active semester found. Skipping sleep reminders.")
            return
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: return
        
        // Get tomorrow's date and day of week
        val tomorrow = LocalDate.now().plusDays(1)
        val tomorrowDayOfWeek = tomorrow.dayOfWeek
        
        // Skip weekends
        if (tomorrowDayOfWeek == DayOfWeek.SATURDAY || tomorrowDayOfWeek == DayOfWeek.SUNDAY) {
            logger.debug("Tomorrow is weekend. Skipping sleep reminders.")
            return
        }
        
        // Map day of week to day name (for matching with database)
        val tomorrowDayName = tomorrowDayOfWeek.name // e.g., "MONDAY", "TUESDAY", etc.
        
        // Get all students with FCM tokens
        val studentsWithFcmToken = studentRepositoryAppAction.findAllWithFcmToken()
        logger.info("Found ${studentsWithFcmToken.size} students with FCM tokens")
        
        var remindersSent = 0
        var studentsChecked = 0
        
        for (student in studentsWithFcmToken) {
            try {
                val studentId = student.id ?: continue
                studentsChecked++
                
                // Get student's timetable for tomorrow
                val timetableEntries = studentTimetableRepositoryAppAction
                    .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
                    .filter { it.day?.name?.uppercase() == tomorrowDayName }
                
                if (timetableEntries.isEmpty()) {
                    continue // No lectures tomorrow for this student
                }
                
                // Find the earliest lecture (first lecture of the day)
                val firstLecture = timetableEntries.minByOrNull { 
                    it.slot?.startTime ?: LocalTime.MAX 
                } ?: continue
                
                val firstLectureTime = firstLecture.slot?.startTime ?: continue
                val subjectName = firstLecture.subject?.name ?: "lecture"
                val subjectId = firstLecture.subject?.id ?: continue
                
                // Calculate: current time + sleep duration = wake time
                // If wake time matches first lecture time, send notification
                val wakeTime = currentTime.plusHours(student.sleepDurationHours.toLong())
                
                // Check if wake time hour matches first lecture time hour
                // We check by hour to allow some flexibility (within the same hour)
                val wakeTimeHour = wakeTime.hour
                val firstLectureHour = firstLectureTime.hour
                
                if (wakeTimeHour == firstLectureHour) {
                    // Check if this is a critical lecture (attendance below minimum criteria)
                    val isCritical = isCriticalLecture(studentId, subjectId)
                    
                    logger.debug(
                        "Student ${student.id} (${student.name}): " +
                        "Current time: $currentTime, " +
                        "Sleep duration: ${student.sleepDurationHours}h, " +
                        "Wake time: $wakeTime, " +
                        "First lecture: $firstLectureTime, " +
                        "Critical: $isCritical"
                    )
                    
                    // Send notification
                    val success = sendSleepReminder(
                        student = student,
                        currentTime = currentTime,
                        wakeTime = wakeTime,
                        firstLectureTime = firstLectureTime,
                        subjectName = subjectName,
                        isCritical = isCritical
                    )
                    
                    if (success) {
                        remindersSent++
                        logger.info(
                            "✅ Sleep reminder sent to student ${student.id} (${student.name}) " +
                            "for lecture at $firstLectureTime (${if (isCritical) "CRITICAL" else "normal"})"
                        )
                    } else {
                        logger.warn(
                            "❌ Failed to send sleep reminder to student ${student.id} (${student.name})"
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing sleep reminder for student ${student.id}: ${e.message}", e)
            }
        }
        
        logger.info(
            "Sleep reminder check completed at ${now}. " +
            "Checked $studentsChecked students, sent $remindersSent reminders"
        )
    }
    
    /**
     * Send sleep reminder notification via FCM.
     * 
     * Logic: If user sleeps NOW (current time), they will wake at wakeTime.
     * If wakeTime matches first lecture time, send notification.
     * 
     * @param student The student to send notification to
     * @param currentTime Current time (when they should sleep)
     * @param wakeTime Calculated wake time (current time + sleep duration)
     * @param firstLectureTime First lecture time tomorrow
     * @param subjectName Name of the subject
     * @param isCritical Whether this is a critical lecture (attendance < minimum criteria)
     * @return true if sent successfully, false otherwise
     */
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
        
        val title = if (isCritical) {
            "⚠️ CRITICAL: Sleep Now!"
        } else {
            "😴 Time to Sleep!"
        }
        
        val body = if (isCritical) {
            "⚠️ URGENT: You have a CRITICAL lecture tomorrow at ${firstLectureTime.format(timeFormatter)} ($subjectName). " +
            "Your attendance is BELOW the minimum requirement! " +
            "Sleep now (${currentTime.format(timeFormatter)}) to wake at ${wakeTime.format(timeFormatter)} and be ready!"
        } else {
            "You have a lecture tomorrow at ${firstLectureTime.format(timeFormatter)} ($subjectName). " +
            "Sleep now (${currentTime.format(timeFormatter)}) to wake at ${wakeTime.format(timeFormatter)} and be well-rested!"
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

