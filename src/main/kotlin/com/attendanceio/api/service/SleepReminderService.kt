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
 * For each user with a first lecture the next day:
 * - Calculates recommended sleep time (lecture start time - sleep duration)
 * - Sends notification at that time
 * - For critical lectures (attendance below minimum), sends high-priority reminder
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
     * Calculate sleep time for a student based on their first lecture tomorrow.
     * Returns null if no lectures scheduled for tomorrow.
     */
    fun calculateSleepTimeForTomorrow(student: DMStudent, tomorrow: LocalDate): LocalTime? {
        val sleepDurationHours = student.sleepDurationHours
        
        // Get tomorrow's day of week (0 = Monday, 4 = Friday)
        val dayOfWeek = tomorrow.dayOfWeek
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return null // No lectures on weekends
        }
        
        val dayIndex = dayOfWeek.value - 1 // Convert to 0-4 (Monday-Friday)
        
        // Get active semester
        // Note: This is a simplified version. In production, you'd need to get the active semester
        // For now, we'll need to get timetable entries for the student
        // This requires semester context which we'll need to add
        
        // Get first lecture time for tomorrow
        // This is a placeholder - actual implementation would query timetable
        // and find the earliest lecture for that day
        
        return null // Placeholder - needs full timetable query implementation
    }

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
     * Send sleep reminder notification.
     * This is a placeholder - actual implementation would integrate with push notification service.
     */
    private fun sendSleepReminder(
        student: DMStudent,
        sleepTime: LocalTime,
        firstLectureTime: LocalTime,
        subjectName: String?,
        isCritical: Boolean
    ) {
        val message = if (isCritical) {
            "⚠️ High Priority: You have a critical lecture tomorrow at $firstLectureTime. " +
            "Your attendance is below the minimum requirement. " +
            "Time to sleep: $sleepTime to ensure you're well-rested!"
        } else {
            "😴 Time to sleep! You have a lecture tomorrow at $firstLectureTime. " +
            "Recommended sleep time: $sleepTime"
        }
        
        logger.info("Sleep reminder for student ${student.id}: $message")
        
        // TODO: Integrate with actual notification service (push notifications, email, etc.)
        // For example:
        // pushNotificationService.send(student.deviceToken, message, priority = if (isCritical) HIGH else NORMAL)
    }

    /**
     * Scheduled task to check and send sleep reminders.
     * Runs every hour at :00 minutes (e.g., 8:00, 9:00, 10:00, etc.)
     */
    @Scheduled(cron = "0 0 * * * ?") // Every hour at minute 0
    fun checkAndSendSleepReminders() {
        val now = LocalDateTime.now()
        val currentHour = now.hour
        logger.info("Checking for sleep reminders at ${now} (hour: $currentHour)")
        
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
        
        for (student in studentsWithFcmToken) {
            try {
                val studentId = student.id ?: continue
                
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
                
                // Calculate sleep time: lecture start time - sleep duration
                val sleepTime = firstLectureTime.minusHours(student.sleepDurationHours.toLong())
                val sleepTimeHour = sleepTime.hour
                
                // Check if current hour matches the sleep time hour
                if (currentHour == sleepTimeHour) {
                    // Check if this is a critical lecture
                    val isCritical = isCriticalLecture(studentId, subjectId)
                    
                    // Send notification
                    val success = sendSleepReminder(
                        student = student,
                        sleepTime = sleepTime,
                        firstLectureTime = firstLectureTime,
                        subjectName = subjectName,
                        isCritical = isCritical
                    )
                    
                    if (success) {
                        remindersSent++
                        logger.info("Sleep reminder sent to student ${student.id} (${student.name}) for lecture at $firstLectureTime")
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing sleep reminder for student ${student.id}: ${e.message}", e)
            }
        }
        
        logger.info("Sleep reminder check completed. Sent $remindersSent reminders at ${now}")
    }
    
    /**
     * Send sleep reminder notification via FCM.
     * Returns true if sent successfully, false otherwise.
     */
    private fun sendSleepReminder(
        student: DMStudent,
        sleepTime: LocalTime,
        firstLectureTime: LocalTime,
        subjectName: String,
        isCritical: Boolean
    ): Boolean {
        val fcmToken = student.fcmToken ?: return false
        
        val title = if (isCritical) {
            "⚠️ High Priority: Sleep Reminder"
        } else {
            "😴 Time to Sleep!"
        }
        
        val body = if (isCritical) {
            "You have a critical lecture tomorrow at ${firstLectureTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))} ($subjectName). " +
            "Your attendance is below minimum. Sleep by ${sleepTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))} to be well-rested!"
        } else {
            "You have a lecture tomorrow at ${firstLectureTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))} ($subjectName). " +
            "Recommended sleep time: ${sleepTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))}"
        }
        
        return fcmNotificationService.sendNotification(
            fcmToken = fcmToken,
            title = title,
            body = body,
            data = mapOf(
                "type" to "sleep_reminder",
                "lectureTime" to firstLectureTime.toString(),
                "sleepTime" to sleepTime.toString(),
                "subjectName" to subjectName,
                "isCritical" to isCritical.toString()
            )
        )
    }
}

