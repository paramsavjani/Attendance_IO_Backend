package com.attendanceio.api.model.student

import com.attendanceio.api.model.timetable.SubjectEnrollmentSyncResult

data class SaveEnrolledSubjectsResult(
    val subjectIds: List<String>,
    val syncResult: SubjectEnrollmentSyncResult
)
