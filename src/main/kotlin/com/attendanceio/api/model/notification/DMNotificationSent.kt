package com.attendanceio.api.model.notification

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate

@Entity
@Table(name = "notification_sent")
class DMNotificationSent : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @Column(name = "notification_time", nullable = false)
    var notificationTime: Int = 18 // 18 for 6 PM, 22 for 10 PM

    @Column(name = "notification_date", nullable = false)
    var notificationDate: LocalDate? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subject_ids", columnDefinition = "jsonb")
    var subjectIds: List<Long>? = null // List of subject IDs for which notification was sent
}
