package com.attendanceio.api.model.feedback

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * One message on a feedback thread. Students submit the feedback itself; everything here is a
 * reply written from the admin site, which inserts rows directly the same way it already flips
 * `feedback.status`. Several replies per thread are allowed so a follow-up ("shipped in 180")
 * doesn't have to overwrite the first answer.
 */
@Entity
@Table(name = "feedback_message")
class DMFeedbackMessage : BaseEntity() {
    @ManyToOne
    @JoinColumn(name = "feedback_id", nullable = false)
    var feedback: DMFeedback? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "author", nullable = false, length = 16)
    var author: FeedbackAuthor = FeedbackAuthor.OWNER

    @Column(name = "body", nullable = false, length = 1000)
    var body: String = ""
}

enum class FeedbackAuthor {
    STUDENT,
    OWNER
}
