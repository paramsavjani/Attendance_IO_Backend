package com.attendanceio.api.model.feedback

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "feedback")
class DMFeedback : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: FeedbackType = FeedbackType.FEEDBACK

    @Column(name = "title", nullable = false, length = 100)
    var title: String = ""

    @Column(name = "description", nullable = false, length = 500)
    var description: String = ""

    // `feedback.status` is deliberately NOT mapped. It is a Postgres enum written only by the
    // admin site, and mapping it makes ddl-auto=update rewrite the column to varchar, which
    // breaks that site's `status = $1::feedback_status` comparisons. Nothing in the app needs
    // it: a thread reads as answered when it has a reply.

    /** Set when a reply is added, so "anything new?" is one indexed comparison. */
    @Column(name = "last_reply_at")
    var lastReplyAt: LocalDateTime? = null

    /** When the student last opened this thread. Null means they have never seen a reply. */
    @Column(name = "student_seen_at")
    var studentSeenAt: LocalDateTime? = null

    /** A reply the student has not opened yet. */
    val hasUnreadReply: Boolean
        get() {
            val replied = lastReplyAt ?: return false
            val seen = studentSeenAt ?: return true
            return replied.isAfter(seen)
        }
}


enum class FeedbackType {
    BUG,
    FEEDBACK,
    SUGGESTION
}

