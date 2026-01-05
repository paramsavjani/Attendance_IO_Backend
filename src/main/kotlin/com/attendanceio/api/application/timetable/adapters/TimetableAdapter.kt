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
            val subjectId = entry.subject?.id?.toString()
            
            // Check if using custom times or standard slot
            if (entry.customStartTime != null && entry.customEndTime != null) {
                // Custom time entry
                TimetableSlotResponse(
                    day = dayIndex,
                    timeSlot = null, // null for custom times
                    subjectId = subjectId,
                    startTime = entry.customStartTime.toString(), // Format: HH:mm
                    endTime = entry.customEndTime.toString() // Format: HH:mm
                )
            } else {
                // Standard slot entry (backward compatibility)
                val timeSlotIndex = (entry.slot?.id?.toInt() ?: 0) - 1
                TimetableSlotResponse(
                    day = dayIndex,
                    timeSlot = timeSlotIndex,
                    subjectId = subjectId,
                    startTime = null,
                    endTime = null
                )
            }
        }
        
        return TimetableResponse(slots = slots)
    }
}

