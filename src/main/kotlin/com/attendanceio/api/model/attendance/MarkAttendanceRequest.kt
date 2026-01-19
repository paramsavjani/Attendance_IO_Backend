package com.attendanceio.api.model.attendance

import java.time.LocalDate

data class MarkAttendanceRequest(
    val subjectId: String,
    val lectureDate: String, // ISO format: "yyyy-MM-dd"
    val status: String, // "present" or "absent"
    val timeSlot: Int? = null, // 0-5 (time slot index) - null for custom times or backward compatibility
    val startTime: String? = null, // Custom start time (HH:mm format) - required if timeSlot is null and using custom times
    val endTime: String? = null, // Custom end time (HH:mm format) - required if timeSlot is null and using custom times
    val isExtraClass: Boolean = false // Flag to indicate this is an extra class added by user
)

data class MarkAttendanceResponse(
    val message: String,
    val attendanceId: Long?,
    val subjectId: String,
    val lectureDate: String,
    val status: String
)

