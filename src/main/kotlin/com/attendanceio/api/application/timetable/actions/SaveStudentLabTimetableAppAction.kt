package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.DMStudentLabTimetable
import com.attendanceio.api.model.timetable.SaveTimetableRequest
import com.attendanceio.api.repository.timetable.StudentLabTimetableRepositoryAppAction
import com.attendanceio.api.service.TimetableSlotProcessingService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SaveStudentLabTimetableAppAction(
    private val studentLabTimetableRepositoryAppAction: StudentLabTimetableRepositoryAppAction,
    private val timetableSlotProcessingService: TimetableSlotProcessingService
) {
    @Transactional
    fun execute(student: DMStudent, request: SaveTimetableRequest): Map<String, Any> {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        val semester = timetableSlotProcessingService.getActiveSemester()
        val semesterId = semester.id ?: throw IllegalArgumentException("Current semester ID is null")

        studentLabTimetableRepositoryAppAction.deleteByStudentIdAndSemesterId(studentId, semesterId)

        val newEntries = timetableSlotProcessingService.resolveSlots(request.slots).map { slot ->
            DMStudentLabTimetable().apply {
                this.student = student
                this.semester = slot.semester
                this.subject = slot.subject
                this.day = slot.weekDay
                this.customStartTime = slot.customStartTime
                this.customEndTime = slot.customEndTime
                this.slot = slot.timeSlot
                this.location = slot.location
            }
        }

        if (newEntries.isNotEmpty()) studentLabTimetableRepositoryAppAction.saveAll(newEntries)
        return mapOf("message" to "Lab timetable saved successfully", "count" to newEntries.size)
    }
}
