package com.attendanceio.api.model.attendance

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class MarkAttendanceRequest(
    val subjectId: String,
    val lectureDate: String, // ISO format: "yyyy-MM-dd"
    val status: String, // "present" or "absent"
    val timeSlot: Int? = null, // 0-5 (time slot index) - null for custom times or backward compatibility
    val startTime: String? = null, // Custom start time (HH:mm format) - required if timeSlot is null and using custom times
    val endTime: String? = null, // Custom end time (HH:mm format) - required if timeSlot is null and using custom times
    @get:JsonProperty("isExtraClass") @param:JsonProperty("isExtraClass")
    @JsonAlias("extraClass")
    val isExtraClass: Boolean = false, // Flag: extra class; accept both "isExtraClass" and "extraClass" from client
    val extraClassIndex: Int? = null // 0-based index when multiple extra classes of same subject on same day
)

data class MarkAttendanceResponse(
    val message: String,
    val attendanceId: Long?,
    val subjectId: String,
    val lectureDate: String,
    val status: String
)

