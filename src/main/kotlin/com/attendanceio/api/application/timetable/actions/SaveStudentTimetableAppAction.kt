package com.attendanceio.api.application.timetable.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.model.timetable.SaveTimetableRequest
import com.attendanceio.api.repository.timetable.StudentTimetableRepositoryAppAction
import com.attendanceio.api.service.TimetableSlotProcessingService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SaveStudentTimetableAppAction(
    private val studentTimetableRepositoryAppAction: StudentTimetableRepositoryAppAction,
    private val timetableSlotProcessingService: TimetableSlotProcessingService
) {
    @Transactional
    fun execute(student: DMStudent, request: SaveTimetableRequest): Map<String, Any> {
        val studentId = student.id ?: throw IllegalArgumentException("Student ID is null")
        val semester = timetableSlotProcessingService.getActiveSemester()
        val semesterId = semester.id ?: throw IllegalArgumentException("Current semester ID is null")

        studentTimetableRepositoryAppAction.deleteAllByStudentIdAndSemesterId(studentId, semesterId)

        val resolvedSlots = timetableSlotProcessingService.resolveSlots(request.slots)
        val newEntries = resolvedSlots.map { slot ->
            DMStudentTimetable().apply {
                this.student = student
                this.semester = slot.semester
                this.subject = slot.subject
                this.day = slot.weekDay
                this.customStartTime = slot.customStartTime
                this.customEndTime = slot.customEndTime
                this.slot = slot.timeSlot
            }
        }

        if (newEntries.isNotEmpty()) studentTimetableRepositoryAppAction.saveAll(newEntries)
        return mapOf("message" to "Timetable saved successfully", "count" to newEntries.size)
    }
}
