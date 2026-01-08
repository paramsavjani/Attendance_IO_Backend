package com.attendanceio.api.repository.event

import com.attendanceio.api.model.event.DMUserEvent
import org.springframework.stereotype.Component

@Component
class UserEventRepositoryAppAction(
    private val userEventRepository: UserEventRepository
) {
    fun save(event: DMUserEvent): DMUserEvent {
        return userEventRepository.save(event)
    }

    fun findByStudentId(studentId: Long): List<DMUserEvent> {
        return userEventRepository.findByStudentId(studentId)
    }

    fun findByEventType(eventType: String): List<DMUserEvent> {
        return userEventRepository.findByEventType(eventType)
    }

    fun findByStudentIdAndEventType(studentId: Long, eventType: String): List<DMUserEvent> {
        return userEventRepository.findByStudentIdAndEventType(studentId, eventType)
    }
}
