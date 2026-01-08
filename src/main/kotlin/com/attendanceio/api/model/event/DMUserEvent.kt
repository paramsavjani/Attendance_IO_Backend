package com.attendanceio.api.model.event

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "user_event")
class DMUserEvent : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @Column(name = "event_type", nullable = false)
    var eventType: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: Map<String, Any>? = null
}
