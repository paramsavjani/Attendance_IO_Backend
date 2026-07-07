package com.attendanceio.api.model.timetable

data class SaveTimetableRequest(val slots: List<TimetableSlotRequest>)

data class TimetableSlotRequest(
    val day: Int,
    val timeSlot: Int?,
    val subjectId: String?,
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null
)
