package com.attendanceio.api.model.timetable

data class TimetableSlotResponse(
    val day: Int, // 0-4 (Monday-Friday)
    val timeSlot: Int, // 0-5 (time slot index)
    val subjectId: String? // null if no subject assigned
)

data class TimetableResponse(
    val slots: List<TimetableSlotResponse>
)

data class SaveTimetableRequest(
    val slots: List<TimetableSlotRequest>
)

data class TimetableSlotRequest(
    val day: Int,
    val timeSlot: Int,
    val subjectId: String? // null to clear the slot
)

