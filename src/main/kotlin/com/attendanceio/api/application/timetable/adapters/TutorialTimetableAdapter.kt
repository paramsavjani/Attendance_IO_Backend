package com.attendanceio.api.application.timetable.adapters

import com.attendanceio.api.model.timetable.DMStudentTutorialTimetable
import com.attendanceio.api.model.timetable.TimetableResponse
import com.attendanceio.api.model.timetable.TimetableSlotResponse
import org.springframework.stereotype.Component

@Component
class TutorialTimetableAdapter {
    fun toResponse(timetableEntries: List<DMStudentTutorialTimetable>): TimetableResponse {
        val slots = timetableEntries.map { entry ->
            // Convert day_id (1-5) to day index (0-4)
            val dayIndex = (entry.day?.id?.toInt() ?: 0) - 1
            val subjectId = entry.subject?.id?.toString()
            
            // Check if using explicit custom times
            if (entry.customStartTime != null && entry.customEndTime != null) {
                // Explicit custom time entry
                TimetableSlotResponse(
                    day = dayIndex,
                    timeSlot = null,
                    subjectId = subjectId,
                    startTime = entry.customStartTime.toString(),
                    endTime = entry.customEndTime.toString()
                )
            } else {
                // Check if slot exists
                val slot = entry.slot
                if (slot != null) {
                    // Slot-based entry - use slot times
                    val slotStartTime = slot.startTime?.toString()?.substring(0, 5) // HH:mm format
                    val slotEndTime = slot.endTime?.toString()?.substring(0, 5) // HH:mm format
                    
                    // Check if slot is in standard range (1-6, which maps to 0-5)
                    val slotId = slot.id?.toInt() ?: 0
                    val isStandardSlot = slotId >= 1 && slotId <= 6
                    
                    if (isStandardSlot && slotStartTime != null && slotEndTime != null) {
                        // Standard slot (0-5) - can use timeSlot index
                        val timeSlotIndex = slotId - 1
                        TimetableSlotResponse(
                            day = dayIndex,
                            timeSlot = timeSlotIndex,
                            subjectId = subjectId,
                            startTime = null,
                            endTime = null
                        )
                    } else {
                        // Custom time slot (outside standard range) - use times as custom
                        TimetableSlotResponse(
                            day = dayIndex,
                            timeSlot = null,
                            subjectId = subjectId,
                            startTime = slotStartTime,
                            endTime = slotEndTime
                        )
                    }
                } else {
                    // No slot and no custom times - invalid entry, skip or return null
                    TimetableSlotResponse(
                        day = dayIndex,
                        timeSlot = null,
                        subjectId = subjectId,
                        startTime = null,
                        endTime = null
                    )
                }
            }
        }
        
        return TimetableResponse(slots = slots)
    }
}
