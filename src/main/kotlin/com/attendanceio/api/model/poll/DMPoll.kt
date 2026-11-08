package com.attendanceio.api.model.poll

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * A question a student asks the rest of one subject's class. Votes are always anonymous;
 * [anonymous] only hides who asked. Polls are never removed from the database: deleting one sets
 * [deletedAt], and its options, votes and reports stay as a record.
 */
@Entity
@Table(
    name = "poll",
    indexes = [
        Index(name = "idx_poll_subject_created", columnList = "subject_id, created_at"),
        Index(name = "idx_poll_creator_created", columnList = "creator_id, created_at")
    ]
)
class DMPoll : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: DMSubject? = null

    @ManyToOne
    @JoinColumn(name = "creator_id", nullable = false)
    var creator: DMStudent? = null

    /** The creator's name when they asked, kept for the record even if it is anonymous or later changes. */
    @Column(name = "creator_name", length = 100)
    var creatorName: String? = null

    @Column(name = "question", nullable = false, length = 200)
    var question: String = ""

    @Column(name = "allow_multiple", nullable = false)
    var allowMultiple: Boolean = false

    /** Hides the creator's name from classmates. */
    @Column(name = "anonymous", nullable = false)
    var anonymous: Boolean = false

    @Column(name = "closes_at", nullable = false)
    var closesAt: Instant = Instant.now()

    /** Set when the creator ends the poll before [closesAt]. */
    @Column(name = "closed_at")
    var closedAt: Instant? = null

    /** Set once enough classmates report it; only the creator still sees it. */
    @Column(name = "hidden", nullable = false)
    var hidden: Boolean = false

    /** Set when the creator deletes it. A deleted poll is gone for everyone, but its data stays. */
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    val isDeleted: Boolean
        get() = deletedAt != null

    /** When voting stopped (or will stop). */
    val endsAt: Instant
        get() = closedAt ?: closesAt

    fun isOpen(now: Instant = Instant.now()): Boolean =
        !isDeleted && !hidden && closedAt == null && now.isBefore(closesAt)
}
