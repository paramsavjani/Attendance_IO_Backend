package com.attendanceio.api.service

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
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
import java.util.concurrent.TimeUnit

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
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction
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
     * Runs every hour to check if it's time to send reminders.
     */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    fun checkAndSendSleepReminders() {
        logger.debug("Checking for sleep reminders...")
        
        val now = LocalDateTime.now()
        val tomorrow = LocalDate.now().plusDays(1)
        
        // This is a placeholder implementation
        // Full implementation would:
        // 1. Get all students with enrolled subjects
        // 2. For each student, get their timetable for tomorrow
        // 3. Find first lecture of the day
        // 4. Calculate sleep time
        // 5. If current time matches sleep time, send notification
        
        // For now, we'll just log that the service is running
        logger.debug("Sleep reminder check completed at ${now}")
    }
}

