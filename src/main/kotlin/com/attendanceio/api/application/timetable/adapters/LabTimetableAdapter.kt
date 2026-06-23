package com.attendanceio.api.application.timetable.adapters

import com.attendanceio.api.model.timetable.DMStudentLabTimetable
import com.attendanceio.api.model.timetable.TimetableResponse
import org.springframework.stereotype.Component

@Component
class LabTimetableAdapter {
    fun toResponse(entries: List<DMStudentLabTimetable>): TimetableResponse = TimetableResponse(
        entries.map { mapLabTutEntry(it.day, it.subject, it.slot, it.customStartTime, it.customEndTime, it.location) }
    )
}
