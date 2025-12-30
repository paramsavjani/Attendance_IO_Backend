package com.attendanceio.api.model.student

data class EnrolledSubjectResponse(
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val lecturePlace: String? = null,
    val color: String = "#3B82F6",
    val minimumCriteria: Int? = null
)

data class EnrolledSubjectsResponse(
    val subjects: List<EnrolledSubjectResponse>
)

data class SaveEnrolledSubjectsRequest(
    val subjectIds: List<String>,
    val conflictResolutions: Map<String, String>? = null // Map of "dayId-slotId" -> "selectedSubjectId"
)

data class UpdateMinimumCriteriaRequest(
    val subjectId: String,
    val minimumCriteria: Int?
)

data class UpdateSleepDurationRequest(
    val sleepDurationHours: Int
)

data class SleepDurationResponse(
    val sleepDurationHours: Int
)

data class SaveBaselineAttendanceRequest(
    val subjectId: String,
    val cutoffDate: String, // ISO date string (yyyy-MM-dd)
    val totalClasses: Int,
    val presentClasses: Int
)

data class BaselineAttendanceResponse(
    val subjectId: String,
    val cutoffDate: String?,
    val totalClasses: Int?,
    val presentClasses: Int?
)

