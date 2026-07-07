package com.attendanceio.api.model.timetable

data class TimetableSlotResponse(
    val day: Int,
    val timeSlot: Int?,
    val subjectId: String?,
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null
)

data class TimetableResponse(val slots: List<TimetableSlotResponse>)
