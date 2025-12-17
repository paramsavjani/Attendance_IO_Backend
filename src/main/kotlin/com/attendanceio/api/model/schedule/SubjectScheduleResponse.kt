package com.attendanceio.api.model.schedule

/**
 * Response model for subject schedule information.
 * Used for conflict detection in the frontend.
 */
data class SubjectScheduleResponse(
    val subjectId: Long,
    val subjectCode: String,
    val subjectName: String,
    val dayId: Int,
    val dayName: String,
    val slotId: Int,
    val slotStartTime: String,
    val slotEndTime: String
)

