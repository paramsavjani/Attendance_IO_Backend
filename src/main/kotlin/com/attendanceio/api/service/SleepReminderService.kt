package com.attendanceio.api.service

import com.attendanceio.api.model.attendance.AttendanceStatus
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.service.ClassCalculationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Service to calculate and send sleep reminders based on lecture schedules.
 * 
 * Production Logic:
 * - Runs every hour (0:00, 1:00, 2:00, etc.) but only processes after 17:00 or in early morning (0:00-7:59)
 * - For each student with FCM token:
 *   1. If checking in early morning (0:00-7:59), gets their first lecture time today
 *   2. If checking in evening/night (17:00-23:59), gets their first lecture time tomorrow
 *   3. Calculates: current time + sleep duration = wake time
 *   4. If wake time matches first lecture time, sends notification NOW
 *   5. If lecture is critical (attendance < minimum criteria), sends critical reminder
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
    private val fcmNotificationService: FcmNotificationService,
    private val classCalculationService: ClassCalculationService
) {
    private val logger = LoggerFactory.getLogger(SleepReminderService::class.java)


    /**
     * Check if a lecture is critical (attendance below minimum criteria).
     * 
     * Total classes calculation:
     * 1. Get student's timetable for the subject
     * 2. Calculate total expected classes from start date to today using ClassCalculationService
     * 3. Subtract cancelled classes from the total
     * 4. Calculate percentage: (present / total) * 100
     */
    fun isCriticalLecture(studentId: Long, subjectId: Long): Boolean {
        logger.debug("Checking if lecture is critical: studentId=$studentId, subjectId=$subjectId")
        
        val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            ?: return false
        
        val minimumCriteria = studentSubject.minimumCriteria ?: return false
        
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            return false
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: return false
        
        // Get student's timetable entries for this subject
        val subjectTimetableEntries = studentTimetableRepositoryAppAction
            .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
            .filter { it.subject?.id == subjectId }
        
        if (subjectTimetableEntries.isEmpty()) {
            return false
        }
        
        // Calculate total expected classes from start date to today
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val computedTotalClasses = classCalculationService.calculateTotalClasses(
            subjectTimetableEntries,
            today
        )
        
        // Get all attendance records for this student and subject
        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
        
        // Count cancelled classes from timetable (not custom time classes)
        // Only subtract cancelled classes that were in the timetable
        val cancelledFromTimetableCount = allAttendanceRecords
            .filter { 
                it.lectureDate != null && 
                !it.lectureDate!!.isAfter(today) &&
                it.status == AttendanceStatus.CANCELLED &&
                // Only count cancelled classes that don't have custom times (i.e., from timetable)
                it.customStartTime == null && it.customEndTime == null
            }
            .size
        
        // Count custom time classes (classes with custom_start_time and custom_end_time)
        // These are classes that happened but weren't in the regular timetable
        // Count all custom time classes (PRESENT, ABSENT, or CANCELLED) as they represent actual scheduled classes
        val customTimeClassesCount = allAttendanceRecords
            .filter { 
                it.lectureDate != null && 
                !it.lectureDate!!.isAfter(today) &&
                it.customStartTime != null && 
                it.customEndTime != null
            }
            .size
        
        logger.debug(
            "Cancelled timetable classes for studentId=$studentId, subjectId=$subjectId: $cancelledFromTimetableCount, " +
            "Custom time classes: $customTimeClassesCount"
        )
        
        // Calculate actual total classes (expected - cancelled timetable classes + custom time classes)
        val totalClasses = maxOf(0, computedTotalClasses - cancelledFromTimetableCount + customTimeClassesCount)
        
        if (totalClasses == 0) {
            logger.debug("Total classes is 0 for studentId=$studentId, subjectId=$subjectId (cannot calculate percentage)")
            return false
        }
        
        // Get attendance stats for this subject
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        val subjectStats = attendanceResults.find { it.subjectId == subjectId } ?: return false
        
        val totalPresent = subjectStats.basePresent + subjectStats.presentAfterCutoff
        val totalAbsent = subjectStats.baseAbsent + subjectStats.absentAfterCutoff
        
        // Calculate percentage based on corrected total classes
        val percentage = (totalPresent.toDouble() / totalClasses) * 100
        val isCritical = percentage < minimumCriteria
        
        logger.info(
            "Critical check result for studentId=$studentId, subjectId=$subjectId: " +
            "attendance=$percentage%, minimum=$minimumCriteria%, isCritical=$isCritical " +
            "(totalClasses=$totalClasses, cancelledFromTimetable=$cancelledFromTimetableCount, customTimeClasses=$customTimeClassesCount)"
        )
        
        return isCritical
    }


    /**
     * Scheduled task to check and send sleep reminders.
     * Runs every hour at :00 minutes (e.g., 8:00, 9:00, 10:00, etc.)
     * 
     * Logic:
     * - Only runs after 17:00 (5:00 PM) or in early morning (0:00-7:59)
     * - For each student: current time + sleep duration = wake time
     * - If checking in early morning (0:00-7:59), check today's lectures
     * - If checking in evening/night (17:00-23:59), check tomorrow's lectures
     * - If wake time matches first lecture time, send notification NOW
     * - If the lecture is critical (attendance < minimum criteria), send critical reminder
     */
    @Scheduled(cron = "0 0 * * * ?", zone = "Asia/Kolkata") // Every hour at minute 0 (IST timezone)
    fun checkAndSendSleepReminders() {
        // Use IST timezone explicitly
        val istZone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.now(istZone)
        val currentTime = now.toLocalTime()
        val currentHour = now.hour
        logger.info("Checking for sleep reminders at ${now} IST (current hour: $currentHour)")
        
        // Only check after 17:00 (5:00 PM) or in early morning (0:00-7:59)
        // Skip checks between 8:00 and 16:59
        if (currentHour >= 8 && currentHour < 17) {
            logger.debug("Sleep reminder check skipped - only runs after 17:00 or in early morning (0:00-7:59)")
            return
        }
        
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            logger.debug("No active semester found. Skipping sleep reminders.")
            return
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: return
        
        // Determine which day's lectures to check:
        // - If checking in early morning (0:00 to 7:59), check today's morning lectures
        // - If checking in evening/night (17:00 to 23:59), check tomorrow's lectures
        val targetDate: LocalDate
        val targetDayOfWeek: DayOfWeek
        
        if (currentHour >= 0 && currentHour < 8) {
            // Early morning (0:00 to 7:59) - check today's lectures
            targetDate = now.toLocalDate()
            targetDayOfWeek = targetDate.dayOfWeek
            logger.debug("Early morning check (${currentHour}:00) - checking today's lectures for ${targetDate}")
        } else {
            // Evening/night (17:00 to 23:59) - check tomorrow's lectures
            targetDate = now.toLocalDate().plusDays(1)
            targetDayOfWeek = targetDate.dayOfWeek
            logger.debug("Evening/night check (${currentHour}:00) - checking tomorrow's lectures for ${targetDate}")
        }
        
        // Skip weekends
        if (targetDayOfWeek == DayOfWeek.SATURDAY || targetDayOfWeek == DayOfWeek.SUNDAY) {
            logger.debug("Target day ($targetDate) is weekend. Skipping sleep reminders.")
            return
        }
        
        // Map day of week to day name (for matching with database)
        val targetDayName = targetDayOfWeek.name // e.g., "MONDAY", "TUESDAY", etc.
        
        // Get all students with FCM tokens
        val studentsWithFcmToken = studentRepositoryAppAction.findAllWithFcmToken()
        logger.info("Found ${studentsWithFcmToken.size} students with FCM tokens")
        
        var remindersSent = 0
        var studentsChecked = 0
        
        for (student in studentsWithFcmToken) {
            try {
                val studentId = student.id ?: continue
                studentsChecked++
                
                // Get student's timetable for target day (today or tomorrow)
                val timetableEntries = studentTimetableRepositoryAppAction
                    .findByStudentIdAndSemesterIdWithDetails(studentId, currentSemesterId)
                    .filter { it.day?.name?.uppercase() == targetDayName }
                
                if (timetableEntries.isEmpty()) {
                    continue // No lectures on target day for this student
                }
                
                // Find the earliest lecture (first lecture of the day)
                val firstLecture = timetableEntries.minByOrNull { 
                    it.slot?.startTime ?: LocalTime.MAX 
                } ?: continue
                
                val firstLectureTime = firstLecture.slot?.startTime ?: continue
                val firstLectureSubjectName = firstLecture.subject?.name ?: "lecture"
                val firstLectureSubjectId = firstLecture.subject?.id ?: continue
                
                // Find the first critical lecture (if any)
                val firstCriticalLecture = timetableEntries
                    .filter { entry ->
                        val subjectId = entry.subject?.id ?: return@filter false
                        isCriticalLecture(studentId, subjectId)
                    }
                    .minByOrNull { it.slot?.startTime ?: LocalTime.MAX }
                
                val firstCriticalLectureTime = firstCriticalLecture?.slot?.startTime
                val firstCriticalLectureSubjectName = firstCriticalLecture?.subject?.name ?: "lecture"
                val firstCriticalLectureSubjectId = firstCriticalLecture?.subject?.id
                
                // Check if first lecture is critical
                val isFirstLectureCritical = isCriticalLecture(studentId, firstLectureSubjectId)
                
                // Calculate: current time + sleep duration = wake time
                val wakeTime = currentTime.plusHours(student.sleepDurationHours.toLong())
                val wakeTimeHour = wakeTime.hour
                
                // Send notification for first lecture (if wake time matches)
                val firstLectureHour = firstLectureTime.hour
                if (wakeTimeHour == firstLectureHour) {
                    // If first lecture is critical, send only critical notification
                    // Otherwise, send general notification
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
                            "for first lecture at $firstLectureTime (${if (isFirstLectureCritical) "CRITICAL" else "normal"})"
                        )
                    } else {
                        logger.warn(
                            "❌ Failed to send sleep reminder to student ${student.id} (${student.name}) " +
                            "for first lecture"
                        )
                    }
                }
                
                // Send notification for first critical lecture (if different from first lecture and wake time matches)
                if (firstCriticalLectureTime != null && 
                    firstCriticalLectureSubjectId != null &&
                    firstCriticalLectureTime != firstLectureTime) {
                    
                    val firstCriticalLectureHour = firstCriticalLectureTime.hour
                    if (wakeTimeHour == firstCriticalLectureHour) {
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
                                "❌ Failed to send CRITICAL sleep reminder to student ${student.id} (${student.name}) " +
                                "for critical lecture"
                            )
                        }
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
     * @param firstLectureTime First lecture time (today or tomorrow depending on check time)
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
            "You have a CRITICAL lecture at ${firstLectureTime.format(timeFormatter)} ($subjectName). " +
            "Your attendance is BELOW the minimum requirement! "
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

