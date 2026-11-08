package com.attendanceio.api.model.poll

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * One chosen option. A multiple-choice ballot is several rows. Counts are always computed from
 * the active rows rather than kept on the option, so concurrent votes can never drift a tally.
 *
 * Rows are never deleted: changing or withdrawing a vote stamps [removedAt] on the old rows, so
 * the table is a full history of who voted for what and when. That history stays in the
 * database; the API only ever returns totals. One active row per (option, student) is enforced
 * by the partial unique index in [com.attendanceio.api.config.PollSchemaPatch].
 */
@Entity
@Table(
    name = "poll_vote",
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

    /** When this choice was replaced or withdrawn. Null while it counts. */
    @Column(name = "removed_at")
    var removedAt: Instant? = null
}
