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
}

enum class FeedbackType {
    BUG,
    FEEDBACK,
    SUGGESTION
}

