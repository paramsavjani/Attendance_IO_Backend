package com.attendanceio.api.application.timetable.adapters

import com.attendanceio.api.model.timetable.DMTimeSlot
import com.attendanceio.api.model.timetable.DMWeekDay
import com.attendanceio.api.model.timetable.TimetableSlotResponse
import com.attendanceio.api.model.subject.DMSubject
import java.time.LocalTime

internal fun mapLabTutEntry(
    day: DMWeekDay?,
    subject: DMSubject?,
    slot: DMTimeSlot?,
    customStartTime: LocalTime?,
    customEndTime: LocalTime?,
    location: String?
): TimetableSlotResponse {
    val dayIndex = (day?.id?.toInt() ?: 0) - 1
    val subjectId = subject?.id?.toString()

    if (customStartTime != null && customEndTime != null) {
        return TimetableSlotResponse(
            day = dayIndex, timeSlot = null, subjectId = subjectId,
            startTime = customStartTime.toString(), endTime = customEndTime.toString(),
            location = location
        )
    }

    if (slot != null) {
        // Always emit the slot's actual times. Lab/tutorial classes are rendered purely
        // by time (any time of day, any duration), so we never collapse them to a fixed
        // lecture-slot index - that would hide slots the client only renders when times exist.
        return TimetableSlotResponse(
            day = dayIndex, timeSlot = null, subjectId = subjectId,
            startTime = slot.startTime?.toString()?.substring(0, 5),
            endTime = slot.endTime?.toString()?.substring(0, 5),
            location = location
        )
    }

    return TimetableSlotResponse(
        day = dayIndex, timeSlot = null, subjectId = subjectId,
        startTime = null, endTime = null, location = location
    )
}
