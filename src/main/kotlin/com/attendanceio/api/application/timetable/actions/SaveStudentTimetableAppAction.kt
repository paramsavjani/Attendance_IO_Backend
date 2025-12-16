package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.model.timetable.DMTimeSlot
import com.attendanceio.api.model.timetable.DMWeekDay
import com.attendanceio.api.model.timetable.SaveTimetableRequest
import com.attendanceio.api.repository.semester.SemesterRepositoryAppAction
import com.attendanceio.api.repository.subject.SubjectRepositoryAppAction
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.repository.timetable.TimeSlotRepository
import com.attendanceio.api.repository.timetable.WeekDayRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SaveStudentTimetableAppAction(
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
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
        
        // Delete existing timetable entries for current semester
        studentTimetableRepositoryAppAction.deleteAllByStudentIdAndSemesterId(studentId, currentSemesterId)
        
        // Get all week days and time slots for mapping
        val weekDays = weekDayRepository.findAll().associateBy { it.id }
        val timeSlots = timeSlotRepository.findAll().associateBy { it.id }
        
        // Create new timetable entries
        val newEntries = request.slots
            .filter { it.subjectId != null } // Only create entries for slots with subjects
            .mapNotNull { slotRequest ->
                // day 0-4 (Monday-Friday) maps to day_id 1-5 in database
                // timeSlot 0-5 maps to slot_id 1-6 in database
                val dayId = (slotRequest.day + 1).toShort()
                val slotId = (slotRequest.timeSlot + 1).toShort()
                
                val weekDay = weekDays[dayId]
                    ?: throw IllegalArgumentException("Week day not found for day: ${slotRequest.day}")
                val timeSlot = timeSlots[slotId]
                    ?: throw IllegalArgumentException("Time slot not found for timeSlot: ${slotRequest.timeSlot}")
                
                val subjectId = slotRequest.subjectId?.toLongOrNull()
                    ?: return@mapNotNull null
                
                val subject = subjectRepositoryAppAction.findById(subjectId)
                    ?: throw IllegalArgumentException("Subject not found: ${slotRequest.subjectId}")
                
                DMStudentTimetable().apply {
                    this.student = student
                    this.semester = currentSemester
                    this.subject = subject
                    this.day = weekDay
                    this.slot = timeSlot
                }
            }
        
        // Save all new entries
        if (newEntries.isNotEmpty()) {
            studentTimetableRepositoryAppAction.saveAll(newEntries)
        }
        
        return mapOf(
            "message" to "Timetable saved successfully",
            "count" to newEntries.size
        )
    }
}

