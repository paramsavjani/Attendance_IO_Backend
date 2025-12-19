package com.attendanceio.api.application.student.actions

import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.student.UpdateSleepDurationRequest
import com.attendanceio.api.repository.student.StudentRepositoryAppAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdateSleepDurationAppAction(
    private val studentRepositoryAppAction: StudentRepositoryAppAction
) {
    @Transactional
    fun execute(student: DMStudent, request: UpdateSleepDurationRequest) {
        
        student.sleepDurationHours = request.sleepDurationHours
        studentRepositoryAppAction.update(student)
    }
}

