package com.attendanceio.api.controller.attendance

import com.attendanceio.api.application.attendance.actions.MarkAttendanceAppAction
import com.attendanceio.api.model.attendance.AttendanceCalculationResult
import com.attendanceio.api.model.attendance.MarkAttendanceRequest
import com.attendanceio.api.model.attendance.MarkAttendanceResponse
import com.attendanceio.api.model.attendance.MyAttendanceResponse
import com.attendanceio.api.model.attendance.SubjectStatsResponse
import com.attendanceio.api.model.attendance.TodayAttendanceRecord
import com.attendanceio.api.repository.attendance.AttendanceRepositoryAppAction
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import com.attendanceio.api.repository.student.StudentSubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTutorialTimetableRepositoryAppAction
import com.attendanceio.api.service.AttendanceCalculationService
import com.attendanceio.api.service.ClassCalculationService
import com.attendanceio.api.model.timetable.DMStudentLabTimetable
import com.attendanceio.api.model.timetable.DMStudentTutorialTimetable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.springframework.cache.annotation.CacheEvict
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/attendance")
class AttendanceController(
    private val studentRepositoryAppAction: StudentRepositoryAppAction,
    private val markAttendanceAppAction: MarkAttendanceAppAction,
    private val attendanceRepositoryAppAction: AttendanceRepositoryAppAction,
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val studentTutorialTimetableRepositoryAppAction: StudentTutorialTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val studentSubjectRepositoryAppAction: StudentSubjectRepositoryAppAction,
    private val classCalculationService: ClassCalculationService,
    private val attendanceCalculationService: AttendanceCalculationService
) {
    @GetMapping
    fun getMyAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestParam(required = false) date: String?
    ): ResponseEntity<MyAttendanceResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        // For demo users, use student ID 790; for regular users, use their own ID
        val studentId = if (com.attendanceio.api.util.DemoUserUtil.isDemoUser(oauth2User)) {
            com.attendanceio.api.util.DemoUserUtil.DEMO_STUDENT_ID
        } else {
            val email = oauth2User.getAttribute<String>("email") ?: ""
            val student = studentRepositoryAppAction.findByEmail(email)
                ?: return ResponseEntity.status(404).build()
            student.id ?: return ResponseEntity.status(404).build()
        }
        
        // Parse date parameter or use today
        val targetDate = try {
            date?.let { LocalDate.parse(it) } ?: LocalDate.now()
        } catch (e: Exception) {
            return ResponseEntity.status(400).build()
        }
        
        // Get attendance statistics for all subjects
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
        
        // Get student's timetable for current semester
        val currentSemester = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()
        val studentTimetable = if (currentSemester != null) {
            studentTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)
        } else {
            emptyList()
        }
        
        // Get lab/tutorial timetables to filter out lab/tutorial attendance
        val labTimetable = if (currentSemester != null) {
            studentLabTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)
        } else {
            emptyList()
        }
        val tutorialTimetable = if (currentSemester != null) {
            studentTutorialTimetableRepositoryAppAction.findByStudentIdAndSemesterId(studentId, currentSemester.id!!)
        } else {
            emptyList()
        }
        
        // Helper function to get time slot from lab/tutorial timetable entry
        fun getTimeSlot(entry: Any): Pair<LocalTime?, LocalTime?> {
            return when (entry) {
                is DMStudentLabTimetable -> {
                    val startTime = entry.customStartTime ?: entry.slot?.startTime
                    val endTime = entry.customEndTime ?: entry.slot?.endTime
                    Pair(startTime, endTime)
                }
                is DMStudentTutorialTimetable -> {
                    val startTime = entry.customStartTime ?: entry.slot?.startTime
                    val endTime = entry.customEndTime ?: entry.slot?.endTime
                    Pair(startTime, endTime)
                }
                else -> Pair(null, null)
            }
        }
        
        // Create a set of all lab/tutorial time slots for filtering
        val allLabTutEntries: List<Any> = (labTimetable.map { it as Any } + tutorialTimetable.map { it as Any })
        val allLabTutTimeSlots = allLabTutEntries.mapNotNull { entry ->
            val (startTime, endTime) = getTimeSlot(entry)
            if (startTime != null && endTime != null) {
                Pair(startTime, endTime)
            } else {
                null
            }
        }.toSet()
        
        // Get all attendance records to count cancelled classes and filter lab/tutorial
        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)
        
        // Filter out lab/tutorial attendance records (only keep lecture attendance)
        val lectureOnlyAttendanceRecords = allAttendanceRecords.filter { attendance ->
            // If attendance has custom times, check if it matches any lab/tutorial slot
            if (attendance.customStartTime != null && attendance.customEndTime != null) {
                val timePair = Pair(attendance.customStartTime!!, attendance.customEndTime!!)
                // Exclude if it matches a lab/tutorial time slot
                timePair !in allLabTutTimeSlots
            } else {
                // Standard time slot or old format - assume it's a lecture
                true
            }
        }
        
        // Convert to subject stats with computed total classes (lecture only)
        val subjectStats = attendanceResults.map { result ->
            // Recalculate present/absent excluding lab/tutorial attendance
            val subjectId = result.subjectId
            
            // Filter attendance records for this subject, excluding lab/tutorial
            // Also filter by targetDate to match the total classes calculation
            val subjectLectureAttendance = lectureOnlyAttendanceRecords.filter { 
                it.subject?.id == subjectId &&
                it.lectureDate != null &&
                !it.lectureDate!!.isAfter(targetDate)
            }
            
            // Count present/absent from lecture-only records up to target date
            // Count all lecture attendance (present and absent) excluding cancelled
            // This includes extra classes, regular timetable classes, custom time classes, and slot-based classes
            val lecturePresent = subjectLectureAttendance.count { 
                it.status == com.attendanceio.api.model.attendance.AttendanceStatus.PRESENT 
            }
            val lectureAbsent = subjectLectureAttendance.count { 
                it.status == com.attendanceio.api.model.attendance.AttendanceStatus.ABSENT 
            }
            
            // Use lecture-only counts
            val totalPresent = lecturePresent
            val totalAbsent = lectureAbsent
            
            // Get timetable entries for this subject
            val subjectTimetableEntries = studentTimetable.filter { 
                it.subject?.id == result.subjectId 
            }
            
            // Calculate total expected classes based on timetable up to target date
            val computedTotalClasses = classCalculationService.calculateTotalClasses(
                subjectTimetableEntries,
                targetDate
            )
            
            // Calculate total expected classes until end date (April 30)
            val endDate = classCalculationService.getConfiguredEndDate() ?: targetDate
            val computedTotalUntilEndDate = classCalculationService.calculateTotalClasses(
                subjectTimetableEntries,
                endDate
            )
            
            // Step 1: Calculate total custom time classes (count ALL, including cancelled)
            // Custom time classes are those with custom_start_time and custom_end_time
            val customTimeClassesCount = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate) &&
                    it.customStartTime != null && 
                    it.customEndTime != null &&
                    !it.isExtraClass // Exclude extra classes from custom time count
                }
                .size
            
            // Step 2: Calculate total slot-based classes (count ALL, including cancelled)
            // Slot-based classes are those with time_slot_id (but no custom times)
            val slotBasedClassesCount = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate) &&
                    it.timeSlot != null &&
                    it.customStartTime == null && 
                    it.customEndTime == null &&
                    !it.isExtraClass // Exclude extra classes from slot-based count
                }
                .size
            
            // Step 2.5: Calculate total extra classes (count ALL, including cancelled)
            // Extra classes are those with isExtraClass = true and no time information
            val extraClassesCount = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate) &&
                    it.isExtraClass &&
                    it.timeSlot == null &&
                    it.customStartTime == null && 
                    it.customEndTime == null
                }
                .size
            
            // Get dates that have custom time classes or slot-based classes (these replace timetable classes for those dates)
            val datesWithCustomOrSlotClasses = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate) &&
                    ((it.customStartTime != null && it.customEndTime != null) || it.timeSlot != null)
                }
                .mapNotNull { it.lectureDate }
                .toSet()
            
            // Step 3: Calculate total classes from timetable schedule (excluding dates with custom/slot classes)
            // This counts classes based on the timetable schedule, but skips dates that have custom or slot-based classes
            val timetableClassesCount = if (computedTotalClasses > 0 && subjectTimetableEntries.isNotEmpty()) {
                val startDate = classCalculationService.getConfiguredStartDate()
                if (startDate != null && !targetDate.isBefore(startDate)) {
                    // Get timetable day slots
                    val timetableDaySlots = subjectTimetableEntries.mapNotNull { entry ->
                        val dayName = entry.day?.name?.uppercase()
                        when (dayName) {
                            "MONDAY" -> DayOfWeek.MONDAY
                            "TUESDAY" -> DayOfWeek.TUESDAY
                            "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                            "THURSDAY" -> DayOfWeek.THURSDAY
                            "FRIDAY" -> DayOfWeek.FRIDAY
                            "SATURDAY" -> DayOfWeek.SATURDAY
                            "SUNDAY" -> DayOfWeek.SUNDAY
                            else -> null
                        }
                    }
                    
                    // Count timetable classes day by day, excluding dates with custom/slot classes
                    var timetableCount = 0
                    var currentDate: LocalDate = startDate
                    while (!currentDate.isAfter(targetDate)) {
                        // Only count timetable classes if this date doesn't have custom or slot-based classes
                        if (currentDate !in datesWithCustomOrSlotClasses) {
                            val dayOfWeek = currentDate.dayOfWeek
                            val matchingEntries = timetableDaySlots.count { it == dayOfWeek }
                            timetableCount += matchingEntries
                        }
                        currentDate = currentDate.plusDays(1)
                    }
                    timetableCount
                } else {
                    0
                }
            } else {
                0
            }
            
            // Step 4: Count all cancelled classes (custom time, slot-based, and timetable)
            val totalCancelledCount = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate) &&
                    it.status == com.attendanceio.api.model.attendance.AttendanceStatus.CANCELLED
                }
                .size
            
            // Step 5: Calculate total classes
            // Count ALL actual attendance records (present + absent + cancelled) - this is the base
            // This ensures we always count actual classes that happened, regardless of type
            val allAttendanceRecordsCount = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate)
                }
                .size
            
            // Add timetable classes for dates that don't have any attendance records
            // This handles scheduled classes that haven't happened yet
            val attendanceDates = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate)
                }
                .mapNotNull { it.lectureDate }
                .toSet()
            
            val timetableClassesWithoutAttendance = if (computedTotalClasses > 0 && subjectTimetableEntries.isNotEmpty()) {
                val startDate = classCalculationService.getConfiguredStartDate()
                if (startDate != null && !targetDate.isBefore(startDate)) {
                    val timetableDaySlots = subjectTimetableEntries.mapNotNull { entry ->
                        val dayName = entry.day?.name?.uppercase()
                        when (dayName) {
                            "MONDAY" -> DayOfWeek.MONDAY
                            "TUESDAY" -> DayOfWeek.TUESDAY
                            "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                            "THURSDAY" -> DayOfWeek.THURSDAY
                            "FRIDAY" -> DayOfWeek.FRIDAY
                            "SATURDAY" -> DayOfWeek.SATURDAY
                            "SUNDAY" -> DayOfWeek.SUNDAY
                            else -> null
                        }
                    }
                    
                    var timetableCount = 0
                    var currentDate: LocalDate = startDate
                    while (!currentDate.isAfter(targetDate)) {
                        // Only count timetable classes if this date doesn't have any attendance records
                        if (currentDate !in attendanceDates) {
                            val dayOfWeek = currentDate.dayOfWeek
                            val matchingEntries = timetableDaySlots.count { it == dayOfWeek }
                            timetableCount += matchingEntries
                        }
                        currentDate = currentDate.plusDays(1)
                    }
                    timetableCount
                } else {
                    0
                }
            } else {
                0
            }
            
            // Calculate total using: custom + slot + extra + timetable - cancelled
            val calculatedTotal = maxOf(0, customTimeClassesCount + slotBasedClassesCount + extraClassesCount + timetableClassesCount - totalCancelledCount)
            
            // Count actual attendance records (present + absent, excluding cancelled) as minimum
            // This includes extra classes, custom time classes, slot-based classes, and regular classes
            val actualAttendanceMin = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(targetDate) &&
                    it.status != com.attendanceio.api.model.attendance.AttendanceStatus.CANCELLED
                }
                .size
            
            // Total = max(calculated total, actual attendance minimum)
            // This ensures we never show 0 when there are actual attendance records
            val totalClasses = maxOf(calculatedTotal, actualAttendanceMin)
            
            // Calculate for end date (for bunkable calculation)
            val customTimeClassesUntilEndDate = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(endDate) &&
                    it.customStartTime != null && 
                    it.customEndTime != null &&
                    !it.isExtraClass // Exclude extra classes
                }
                .size
            
            val slotBasedClassesUntilEndDate = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(endDate) &&
                    it.timeSlot != null &&
                    it.customStartTime == null && 
                    it.customEndTime == null &&
                    !it.isExtraClass // Exclude extra classes
                }
                .size
            
            val extraClassesUntilEndDate = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(endDate) &&
                    it.isExtraClass &&
                    it.timeSlot == null &&
                    it.customStartTime == null && 
                    it.customEndTime == null
                }
                .size
            
            val datesWithCustomOrSlotClassesUntilEndDate = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(endDate) &&
                    ((it.customStartTime != null && it.customEndTime != null) || it.timeSlot != null)
                }
                .mapNotNull { it.lectureDate }
                .toSet()
            
            val timetableClassesUntilEndDate = if (computedTotalUntilEndDate > 0 && subjectTimetableEntries.isNotEmpty()) {
                val startDate = classCalculationService.getConfiguredStartDate()
                if (startDate != null && !endDate.isBefore(startDate)) {
                    val timetableDaySlots = subjectTimetableEntries.mapNotNull { entry ->
                        val dayName = entry.day?.name?.uppercase()
                        when (dayName) {
                            "MONDAY" -> DayOfWeek.MONDAY
                            "TUESDAY" -> DayOfWeek.TUESDAY
                            "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                            "THURSDAY" -> DayOfWeek.THURSDAY
                            "FRIDAY" -> DayOfWeek.FRIDAY
                            "SATURDAY" -> DayOfWeek.SATURDAY
                            "SUNDAY" -> DayOfWeek.SUNDAY
                            else -> null
                        }
                    }
                    
                    var timetableCount = 0
                    var currentDate: LocalDate = startDate
                    while (!currentDate.isAfter(endDate)) {
                        if (currentDate !in datesWithCustomOrSlotClassesUntilEndDate) {
                            val dayOfWeek = currentDate.dayOfWeek
                            val matchingEntries = timetableDaySlots.count { it == dayOfWeek }
                            timetableCount += matchingEntries
                        }
                        currentDate = currentDate.plusDays(1)
                    }
                    timetableCount
                } else {
                    0
                }
            } else {
                0
            }
            
            val totalCancelledUntilEndDate = lectureOnlyAttendanceRecords
                .filter { 
                    it.subject?.id == result.subjectId && 
                    it.lectureDate != null && 
                    !it.lectureDate!!.isAfter(endDate) &&
                    it.status == com.attendanceio.api.model.attendance.AttendanceStatus.CANCELLED
                }
                .size
            
            val totalUntilEndDate = maxOf(totalClasses, maxOf(0, customTimeClassesUntilEndDate + slotBasedClassesUntilEndDate + extraClassesUntilEndDate + timetableClassesUntilEndDate - totalCancelledUntilEndDate))
            
            val finalTotal = maxOf(0, totalClasses) // Ensure total is not negative
            val finalTotalUntilEndDate = maxOf(finalTotal, maxOf(0, totalUntilEndDate)) // At least current total
            
            // Get minimum criteria for this subject (default to 75 if not set)
            val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, result.subjectId)
            val minRequired = studentSubject?.minimumCriteria ?: 75
            
            // Calculate attendance metrics
            val percentage = attendanceCalculationService.calculatePercentage(totalPresent, finalTotal)
            val classesNeeded = attendanceCalculationService.calculateClassesNeeded(totalPresent, finalTotal, minRequired)
            // Calculate bunkable classes based on total until end date
            val bunkableClasses = attendanceCalculationService.calculateBunkableClasses(
                totalPresent, 
                finalTotal, 
                finalTotalUntilEndDate, 
                minRequired
            )
            
            SubjectStatsResponse(
                subjectId = result.subjectId.toString(),
                present = totalPresent,
                absent = totalAbsent,
                total = finalTotal,
                totalUntilEndDate = finalTotalUntilEndDate,
                percentage = percentage,
                classesNeeded = classesNeeded,
                bunkableClasses = bunkableClasses
            )
        }
        
        // Get attendance records for the specified date (explicit query so extra classes are always included)
        val dateRecords = attendanceRepositoryAppAction.findByStudentIdAndLectureDate(studentId, targetDate)
        // For extra-class records (no time info), assign 0-based index per (subject, date) so frontend can key them
        val extraClassIndexByAttendanceId = mutableMapOf<Long, Int>()
        dateRecords
            .filter { it.isExtraClass && it.timeSlot == null && it.customStartTime == null && it.customEndTime == null }
            .groupBy { it.subject?.id to it.lectureDate }
            .forEach { (_, list) ->
                list.sortedBy { it.id }.forEachIndexed { idx, rec ->
                    rec.id?.let { extraClassIndexByAttendanceId[it] = idx }
                }
            }
        val dateAttendanceRecords = dateRecords.map { attendance ->
            TodayAttendanceRecord(
                attendanceId = attendance.id,
                subjectId = attendance.subject?.id?.toString() ?: "",
                lectureDate = attendance.lectureDate?.toString() ?: "",
                status = attendance.status.name.lowercase(),
                timeSlot = attendance.timeSlot?.id?.toInt()?.minus(1), // Convert to 0-based index
                startTime = attendance.customStartTime?.toString(),
                endTime = attendance.customEndTime?.toString(),
                isExtraClass = attendance.isExtraClass,
                extraClassIndex = attendance.id?.let { extraClassIndexByAttendanceId[it] }
            )
        }
        
        val response = MyAttendanceResponse(
            subjectStats = subjectStats,
            todayAttendance = dateAttendanceRecords
        )
        
        return ResponseEntity.ok(response)
    }
    @PostMapping
    fun markAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @RequestBody request: MarkAttendanceRequest
    ): ResponseEntity<MarkAttendanceResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        return try {
            val attendance = markAttendanceAppAction.execute(student, request)
            val response = MarkAttendanceResponse(
                message = "Attendance marked successfully",
                attendanceId = attendance.id,
                subjectId = request.subjectId,
                lectureDate = request.lectureDate,
                status = attendance.status.name.lowercase()
            )
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(400).build()
        } catch (e: Exception) {
            ResponseEntity.status(500).build()
        }
    }
    
    @DeleteMapping("/{attendanceId}")
    @CacheEvict(value = ["analytics"], allEntries = true)
    fun deleteAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?,
        @PathVariable attendanceId: Long
    ): ResponseEntity<Map<String, Any>> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        val email = oauth2User.getAttribute<String>("email") ?: ""
        val student = studentRepositoryAppAction.findByEmail(email)
            ?: return ResponseEntity.status(404).build()
        
        val studentId = student.id ?: return ResponseEntity.status(404).build()
        
        // Find attendance record and verify it belongs to the student
        val attendance = attendanceRepositoryAppAction.findByStudentId(studentId)
            .firstOrNull { it.id == attendanceId }
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Attendance record not found"))
        
        // Delete the attendance record
        attendanceRepositoryAppAction.delete(attendance)
        
        return ResponseEntity.ok(mapOf("message" to "Attendance deleted successfully"))
    }
    
    @GetMapping("/lab-tutorial")
    fun getLabTutorialAttendance(
        @AuthenticationPrincipal oauth2User: OAuth2User?
    ): ResponseEntity<MyAttendanceResponse> {
        if (oauth2User == null) {
            return ResponseEntity.status(401).build()
        }
        
        // For demo users, use student ID 790; for regular users, use their own ID
        val studentId = if (com.attendanceio.api.util.DemoUserUtil.isDemoUser(oauth2User)) {
            com.attendanceio.api.util.DemoUserUtil.DEMO_STUDENT_ID
        } else {
            val email = oauth2User.getAttribute<String>("email") ?: ""
            val student = studentRepositoryAppAction.findByEmail(email)
                ?: return ResponseEntity.status(404).build()
            student.id ?: return ResponseEntity.status(404).build()
        }
        
        // Get current semester
        val currentSemester = semesterRepositoryAppAction.findByIsActive(true).firstOrNull()
            ?: return ResponseEntity.ok(MyAttendanceResponse(emptyList(), emptyList()))
        
        // Get lab and tutorial timetables
        val labTimetable = studentLabTimetableRepositoryAppAction.findByStudentIdAndSemesterId(
            studentId, currentSemester.id!!
        )
        val tutorialTimetable = studentTutorialTimetableRepositoryAppAction.findByStudentIdAndSemesterId(
            studentId, currentSemester.id!!
        )
        
        // Get all attendance records
        val allAttendanceRecords = attendanceRepositoryAppAction.findByStudentId(studentId)
        
        // Helper function to get subject ID from lab/tutorial timetable entry
        fun getSubjectId(entry: Any): Long? {
            return when (entry) {
                is DMStudentLabTimetable -> entry.subject?.id
                is DMStudentTutorialTimetable -> entry.subject?.id
                else -> null
            }
        }
        
        // Helper function to get time slot from lab/tutorial timetable entry
        fun getTimeSlot(entry: Any): Pair<LocalTime?, LocalTime?> {
            return when (entry) {
                is DMStudentLabTimetable -> {
                    val startTime = entry.customStartTime ?: entry.slot?.startTime
                    val endTime = entry.customEndTime ?: entry.slot?.endTime
                    Pair(startTime, endTime)
                }
                is DMStudentTutorialTimetable -> {
                    val startTime = entry.customStartTime ?: entry.slot?.startTime
                    val endTime = entry.customEndTime ?: entry.slot?.endTime
                    Pair(startTime, endTime)
                }
                else -> Pair(null, null)
            }
        }
        
        // Get unique subjects from lab/tutorial timetables
        val subjectIds = (labTimetable.mapNotNull { it.subject?.id } + 
                         tutorialTimetable.mapNotNull { it.subject?.id }).distinct()
        
        // Get attendance results for these subjects
        val attendanceResults = attendanceRepositoryAppAction.calculateStudentAttendanceBySubject(studentId)
            .filter { it.subjectId in subjectIds }
        
        // Calculate stats for each subject
        val subjectStats = attendanceResults.map { result ->
            val subjectId = result.subjectId
            
            // Get lab/tutorial timetable entries for this subject
            val subjectLabEntries = labTimetable.filter { it.subject?.id == subjectId }
            val subjectTutEntries = tutorialTimetable.filter { it.subject?.id == subjectId }
            val subjectLabTutEntries: List<Any> = (subjectLabEntries.map { it as Any } + 
                                                   subjectTutEntries.map { it as Any })
            
            // Create a set of time combinations for matching attendance
            val labTutTimeSlots = subjectLabTutEntries.mapNotNull { entry ->
                val (startTime, endTime) = getTimeSlot(entry)
                if (startTime != null && endTime != null) {
                    Pair(startTime, endTime)
                } else {
                    null
                }
            }.toSet()
            
            // Filter attendance records to only those matching lab/tutorial slots
            val labTutAttendanceRecords = allAttendanceRecords.filter { attendance ->
                attendance.subject?.id == subjectId && 
                attendance.customStartTime != null && 
                attendance.customEndTime != null &&
                Pair(attendance.customStartTime!!, attendance.customEndTime!!) in labTutTimeSlots
            }
            
            // Count present/absent from filtered records
            val present = labTutAttendanceRecords.count { 
                it.status == com.attendanceio.api.model.attendance.AttendanceStatus.PRESENT 
            }
            val absent = labTutAttendanceRecords.count { 
                it.status == com.attendanceio.api.model.attendance.AttendanceStatus.ABSENT 
            }
            val cancelled = labTutAttendanceRecords.count { 
                it.status == com.attendanceio.api.model.attendance.AttendanceStatus.CANCELLED 
            }
            
            // Calculate total expected classes from lab/tutorial timetable
            val targetDate = LocalDate.now()
            val endDate = classCalculationService.getConfiguredEndDate() ?: targetDate
            
            // Calculate total classes using the same logic as regular timetable
            val computedTotalClasses = calculateLabTutTotalClasses(subjectLabTutEntries, targetDate)
            val computedTotalUntilEndDate = calculateLabTutTotalClasses(subjectLabTutEntries, endDate)
            
            // Subtract cancelled classes
            val cancelledCount = labTutAttendanceRecords.count {
                it.lectureDate != null && 
                !it.lectureDate!!.isAfter(targetDate) &&
                it.status == com.attendanceio.api.model.attendance.AttendanceStatus.CANCELLED
            }
            
            val cancelledUntilEndDate = labTutAttendanceRecords.count {
                it.lectureDate != null && 
                !it.lectureDate!!.isAfter(endDate) &&
                it.status == com.attendanceio.api.model.attendance.AttendanceStatus.CANCELLED
            }
            
            val totalClasses = maxOf(0, computedTotalClasses - cancelledCount)
            val totalUntilEndDate = maxOf(totalClasses, maxOf(0, computedTotalUntilEndDate - cancelledUntilEndDate))
            
            // Get minimum criteria
            val studentSubject = studentSubjectRepositoryAppAction.findByStudentIdAndSubjectId(studentId, subjectId)
            val minRequired = studentSubject?.minimumCriteria ?: 75
            
            // Calculate metrics
            val percentage = attendanceCalculationService.calculatePercentage(present, totalClasses)
            val classesNeeded = attendanceCalculationService.calculateClassesNeeded(present, totalClasses, minRequired)
            val bunkableClasses = attendanceCalculationService.calculateBunkableClasses(
                present, 
                totalClasses, 
                totalUntilEndDate, 
                minRequired
            )
            
            SubjectStatsResponse(
                subjectId = subjectId.toString(),
                present = present,
                absent = absent,
                total = totalClasses,
                totalUntilEndDate = totalUntilEndDate,
                percentage = percentage,
                classesNeeded = classesNeeded,
                bunkableClasses = bunkableClasses
            )
        }
        
        // Get today's attendance records for lab/tutorial
        val today = LocalDate.now()
        val todayLabTutAttendance = allAttendanceRecords
            .filter { it.lectureDate == today }
            .filter { attendance ->
                val subjectId = attendance.subject?.id
                if (subjectId == null) return@filter false
                
                val subjectLabEntries = labTimetable.filter { it.subject?.id == subjectId }
                val subjectTutEntries = tutorialTimetable.filter { it.subject?.id == subjectId }
                val subjectLabTutEntries: List<Any> = (subjectLabEntries.map { it as Any } + 
                                                       subjectTutEntries.map { it as Any })
                
                val labTutTimeSlots = subjectLabTutEntries.mapNotNull { entry ->
                    val (startTime, endTime) = getTimeSlot(entry)
                    if (startTime != null && endTime != null) {
                        Pair(startTime, endTime)
                    } else {
                        null
                    }
                }.toSet()
                
                attendance.customStartTime != null && 
                attendance.customEndTime != null &&
                Pair(attendance.customStartTime!!, attendance.customEndTime!!) in labTutTimeSlots
            }
            .map { attendance ->
                TodayAttendanceRecord(
                    attendanceId = attendance.id,
                    subjectId = attendance.subject?.id?.toString() ?: "",
                    lectureDate = attendance.lectureDate?.toString() ?: "",
                    status = attendance.status.name.lowercase(),
                    timeSlot = attendance.timeSlot?.id?.toInt()?.minus(1),
                    startTime = attendance.customStartTime?.toString(),
                    endTime = attendance.customEndTime?.toString()
                )
            }
        
        val response = MyAttendanceResponse(
            subjectStats = subjectStats,
            todayAttendance = todayLabTutAttendance
        )
        
        return ResponseEntity.ok(response)
    }
    
    private fun calculateLabTutTotalClasses(
        timetableEntries: List<*>,
        endDate: LocalDate
    ): Int {
        if (timetableEntries.isEmpty()) {
            return 0
        }
        
        // Get day of week for each entry
        val timetableDaySlots = timetableEntries.mapNotNull { entry ->
            val dayName = when (entry) {
                is DMStudentLabTimetable -> entry.day?.name?.uppercase()
                is DMStudentTutorialTimetable -> entry.day?.name?.uppercase()
                else -> null
            }
            val dayOfWeek = when (dayName) {
                "MONDAY" -> DayOfWeek.MONDAY
                "TUESDAY" -> DayOfWeek.TUESDAY
                "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                "THURSDAY" -> DayOfWeek.THURSDAY
                "FRIDAY" -> DayOfWeek.FRIDAY
                "SATURDAY" -> DayOfWeek.SATURDAY
                "SUNDAY" -> DayOfWeek.SUNDAY
                else -> null
            }
            dayOfWeek
        }
        
        if (timetableDaySlots.isEmpty()) {
            return 0
        }
        
        // Use ClassCalculationService's new method
        return classCalculationService.calculateTotalClassesFromDays(timetableDaySlots, endDate)
    }
}

