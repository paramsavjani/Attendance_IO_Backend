package com.attendanceio.api.model.poll

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "poll_option",
    uniqueConstraints = [UniqueConstraint(name = "uk_poll_option_position", columnNames = ["poll_id", "position"])]
)
class DMPollOption : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "poll_id", nullable = false)
    var poll: DMPoll? = null

    @Column(name = "text", nullable = false, length = 80)
    var text: String = ""

    /** Display order, 0-based. */
    @Column(name = "position", nullable = false)
    var position: Int = 0
}
