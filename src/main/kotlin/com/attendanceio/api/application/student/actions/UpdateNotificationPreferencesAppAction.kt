package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class UpdateNotificationPreferencesAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    data class Request(
        val dailyReminderHours: List<Int>?,
        val afterLectureReminderEnabled: Boolean?
    )

    fun execute(student: DMStudent, request: Request): DMStudent {
        request.dailyReminderHours?.let { hours ->
            require(hours.all { it == 18 || it == 20 || it == 22 }) {
                "dailyReminderHours may only contain 18, 20, 22"
            }
            student.dailyReminderAt18 = 18 in hours
            student.dailyReminderAt20 = 20 in hours
            student.dailyReminderAt22 = 22 in hours
        }
        request.afterLectureReminderEnabled?.let { student.afterLectureReminderEnabled = it }
        studentRepositoryAppAction.update(student)
        return student
    }
}
