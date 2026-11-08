package com.attendanceio.api.model.poll

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * One chosen option. A multiple-choice ballot is several rows. Counts are always computed from
 * these rows rather than kept on the option, so concurrent votes can never drift a tally.
 * Never exposed per student: results are only ever returned as totals.
 */
@Entity
@Table(
    name = "poll_vote",
    uniqueConstraints = [UniqueConstraint(name = "uk_poll_vote_option_student", columnNames = ["option_id", "student_id"])],
    indexes = [
        Index(name = "idx_poll_vote_poll", columnList = "poll_id"),
        Index(name = "idx_poll_vote_student_poll", columnList = "student_id, poll_id")
    ]
)
class DMPollVote : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "poll_id", nullable = false)
    var poll: DMPoll? = null

    @ManyToOne
    @JoinColumn(name = "option_id", nullable = false)
    var option: DMPollOption? = null

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null
}
