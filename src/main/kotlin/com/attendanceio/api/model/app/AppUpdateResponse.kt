package com.attendanceio.api.model.app

data class AppUpdateResponse(
    val isUpdateRequired: Boolean,
    val isCritical: Boolean,
    val title: String,
    val message: String
)
