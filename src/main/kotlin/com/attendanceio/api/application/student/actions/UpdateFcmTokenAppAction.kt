package com.attendanceio.api.application.student.actions

import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class UpdateFcmTokenAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    fun execute(studentId: Long, fcmToken: String?) {
        val student = studentRepositoryAppAction.findById(studentId)
            ?: throw IllegalArgumentException("Student not found: $studentId")
        student.fcmToken = fcmToken
        studentRepositoryAppAction.update(student)
    }
}
