package com.attendanceio.api.application.alumni.actions

import com.attendanceio.api.application.alumni.adapters.toResponse
import com.attendanceio.api.model.alumni.AlumniResponse
import com.attendanceio.api.repository.alumni.AlumniRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class GetAlumniAppAction(
    private val alumniRepositoryAppAction: AlumniRepositoryAppAction
) {
    fun execute(id: Long): AlumniResponse? = alumniRepositoryAppAction.findById(id)?.toResponse()
}
