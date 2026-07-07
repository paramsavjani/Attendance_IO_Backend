package com.attendanceio.api.application.event.actions

import com.attendanceio.api.model.event.DMUserEvent
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.repository.event.UserEventRepositoryAppAction
import org.springframework.stereotype.Component

@Component
class TrackEventAppAction(
    private val userEventRepositoryAppAction: UserEventRepositoryAppAction
) {
    fun execute(student: DMStudent, eventType: String, metadata: Map<String, Any>?): DMUserEvent {
        val event = DMUserEvent().apply {
            this.student = student
            this.eventType = eventType
            this.metadata = metadata
        }
        return userEventRepositoryAppAction.save(event)
    }
}
