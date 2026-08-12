package com.attendanceio.api.model.timetable

data class TimeSlotResponse(
    val index: Int, // slot.id - 1, matches the `timeSlot` index used by TimetableSlotResponse
    val startTime: String, // HH:mm:ss
    val endTime: String // HH:mm:ss
)
