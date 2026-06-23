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
        val slotId = slot.id?.toInt() ?: 0
        return if (slotId in 1..6) {
            TimetableSlotResponse(
                day = dayIndex, timeSlot = slotId - 1, subjectId = subjectId,
                startTime = null, endTime = null, location = location
            )
        } else {
            TimetableSlotResponse(
                day = dayIndex, timeSlot = null, subjectId = subjectId,
                startTime = slot.startTime?.toString()?.substring(0, 5),
                endTime = slot.endTime?.toString()?.substring(0, 5),
                location = location
            )
        }
    }

    return TimetableSlotResponse(
        day = dayIndex, timeSlot = null, subjectId = subjectId,
        startTime = null, endTime = null, location = location
    )
}
