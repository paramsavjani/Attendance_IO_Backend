package com.attendanceio.api.repository.event

import com.attendanceio.api.model.event.DMUserEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserEventRepository : JpaRepository<DMUserEvent, Long> {
    fun findByStudentId(studentId: Long): List<DMUserEvent>
    fun findByEventType(eventType: String): List<DMUserEvent>
    fun findByStudentIdAndEventType(studentId: Long, eventType: String): List<DMUserEvent>
}
