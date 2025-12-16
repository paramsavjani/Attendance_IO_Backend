package com.attendanceio.api.application.timetable.adapters

import com.attendanceio.api.model.timetable.DMStudentTimetable
import com.attendanceio.api.model.timetable.TimetableResponse
import com.attendanceio.api.model.timetable.TimetableSlotResponse
import org.springframework.stereotype.Component

@Component
class TimetableAdapter {
    fun toResponse(timetableEntries: List<DMStudentTimetable>): TimetableResponse {
        val slots = timetableEntries.map { entry ->
            // Convert day_id (1-5) to day index (0-4)
            val dayIndex = (entry.day?.id?.toInt() ?: 0) - 1
            // Convert slot_id (1-6) to timeSlot index (0-5)
            val timeSlotIndex = (entry.slot?.id?.toInt() ?: 0) - 1
            val subjectId = entry.subject?.id?.toString()
            
            TimetableSlotResponse(
                day = dayIndex,
                timeSlot = timeSlotIndex,
                subjectId = subjectId
            )
        }
        
        return TimetableResponse(slots = slots)
    }
}

