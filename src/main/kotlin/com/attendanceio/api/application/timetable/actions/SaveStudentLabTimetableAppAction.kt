package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.DMStudentLabTimetable
import com.attendanceio.api.model.timetable.DMTimeSlot
import com.attendanceio.api.model.timetable.DMWeekDay
import com.attendanceio.api.model.timetable.SaveTimetableRequest
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.TimeSlotRepository
import com.attendanceio.api.repository.timetable.WeekDayRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SaveStudentLabTimetableAppAction(
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val semesterRepositoryAppAction: SemesterRepositoryAppAction,
    private val subjectRepositoryAppAction: SubjectRepositoryAppAction,
    private val weekDayRepository: WeekDayRepository,
    private val timeSlotRepository: TimeSlotRepository
) {
    @Transactional
    fun execute(student: DMStudent, request: SaveTimetableRequest): Map<String, Any> {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        
        // Get current active semester
        val activeSemesters = semesterRepositoryAppAction.findByIsActive(true)
        if (activeSemesters.isEmpty()) {
            throw IllegalArgumentException("No active semester found")
        }
        val currentSemester = activeSemesters.first()
        val currentSemesterId = currentSemester.id ?: throw IllegalArgumentException("Current semester ID is null")
        
        // Delete existing lab timetable entries for current semester
        studentLabTimetableRepositoryAppAction.deleteByStudentIdAndSemesterId(studentId, currentSemesterId)
        
        // Get all week days and time slots for mapping
        val weekDays = weekDayRepository.findAll().associateBy { it.id }
        val timeSlots = timeSlotRepository.findAll().associateBy { it.id }
        
        // Create new lab timetable entries
        val newEntries = request.slots
            .filter { it.subjectId != null }
            .mapNotNull { slotRequest ->
                val dayId = (slotRequest.day + 1).toShort()
                
                val weekDay = weekDays[dayId]
                    ?: throw IllegalArgumentException("Week day not found for day: ${slotRequest.day}")
                
                val subjectId = slotRequest.subjectId?.toLongOrNull()
                    ?: return@mapNotNull null
                
                val subject = subjectRepositoryAppAction.findById(subjectId)
                    ?: throw IllegalArgumentException("Subject not found: ${slotRequest.subjectId}")
                
                val hasCustomTimes = slotRequest.startTime != null && slotRequest.endTime != null
                val hasStandardSlot = slotRequest.timeSlot != null
                
                if (!hasCustomTimes && !hasStandardSlot) {
                    throw IllegalArgumentException("Either timeSlot or startTime/endTime must be provided")
                }
                
                if (hasCustomTimes && hasStandardSlot) {
                    throw IllegalArgumentException("Cannot provide both timeSlot and custom times")
                }
                
                DMStudentLabTimetable().apply {
                    this.student = student
                    this.semester = currentSemester
                    this.subject = subject
                    this.day = weekDay
                    
                    if (hasCustomTimes) {
                        try {
                            this.customStartTime = LocalTime.parse(slotRequest.startTime, DateTimeFormatter.ofPattern("HH:mm"))
                            this.customEndTime = LocalTime.parse(slotRequest.endTime, DateTimeFormatter.ofPattern("HH:mm"))
                            this.slot = null
                        } catch (e: Exception) {
                            throw IllegalArgumentException("Invalid time format. Use HH:mm format (e.g., 14:30)")
                        }
                    } else {
                        val slotId = (slotRequest.timeSlot!! + 1).toShort()
                        val timeSlot = timeSlots[slotId]
                            ?: throw IllegalArgumentException("Time slot not found for timeSlot: ${slotRequest.timeSlot}")
                        this.slot = timeSlot
                        this.customStartTime = null
                        this.customEndTime = null
                    }
                }
            }
        
        // Save all new entries
        if (newEntries.isNotEmpty()) {
            studentLabTimetableRepositoryAppAction.saveAll(newEntries)
        }
        
        return mapOf(
            "message" to "Lab timetable saved successfully",
            "count" to newEntries.size
        )
    }
}
