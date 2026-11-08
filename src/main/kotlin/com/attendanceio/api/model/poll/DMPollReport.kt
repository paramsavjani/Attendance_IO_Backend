package com.attendanceio.api.model.poll

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "poll_report",
    uniqueConstraints = [UniqueConstraint(name = "uk_poll_report_poll_student", columnNames = ["poll_id", "student_id"])]
)
class DMPollReport : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "poll_id", nullable = false)
    var poll: DMPoll? = null

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    var reason: PollReportReason = PollReportReason.OTHER
}

enum class PollReportReason {
    SPAM,
    OFFENSIVE,
    MISLEADING,
    OTHER
}
