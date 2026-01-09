package com.attendanceio.api.model.timetable

data class TimetableSlotResponse(
    val day: Int, // 0-4 (Monday-Friday)
    val timeSlot: Int?, // 0-5 (time slot index) - null for custom times
    val subjectId: String?, // null if no subject assigned
    val startTime: String? = null, // Custom start time (HH:mm format) - null if using standard slot
    val endTime: String? = null, // Custom end time (HH:mm format) - null if using standard slot
    val location: String? = null // Location for lab/tutorial classes
)

data class TimetableResponse(
    val slots: List<TimetableSlotResponse>
)

data class SaveTimetableRequest(
    val slots: List<TimetableSlotRequest>
)

data class TimetableSlotRequest(
    val day: Int,
    val timeSlot: Int?, // 0-5 (time slot index) - null if using custom times
    val subjectId: String?, // null to clear the slot
    val startTime: String? = null, // Custom start time (HH:mm format) - required if timeSlot is null
    val endTime: String? = null, // Custom end time (HH:mm format) - required if timeSlot is null
    val location: String? = null // Location for lab/tutorial classes
)

