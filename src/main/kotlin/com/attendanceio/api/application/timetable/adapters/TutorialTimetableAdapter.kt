package com.attendanceio.api.application.timetable.adapters

import com.attendanceio.api.model.timetable.DMStudentTutorialTimetable
import com.attendanceio.api.model.timetable.TimetableResponse
import org.springframework.stereotype.Component

@Component
class TutorialTimetableAdapter {
    fun toResponse(entries: List<DMStudentTutorialTimetable>): TimetableResponse = TimetableResponse(
        entries.map { mapLabTutEntry(it.day, it.subject, it.slot, it.customStartTime, it.customEndTime, it.location) }
    )
}
