package com.attendanceio.api.model.placement

import com.attendanceio.api.model.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * An interview experience a student sent in from the app. Unlike the published experiences (anonymous Markdown in
 * Mongo), a submission keeps who sent it so it can be checked before it is rewritten and published without a name.
 * Exactly one of [link] (a Google Docs / Drive link) and [body] (typed text or an uploaded .md / .txt file) is set.
 */
@Entity
@Table(
    name = "experience_submission",
    indexes = [
        Index(name = "idx_experience_submission_student", columnList = "student_id"),
        Index(name = "idx_experience_submission_status", columnList = "status")
    ]
)
class DMExperienceSubmission : BaseEntity() {
    @Column(name = "student_id", nullable = false)
    var studentId: Long = 0

    @Column(name = "roll_number", length = 20)
    var rollNumber: String? = null

    @Column(name = "student_name", length = 255)
    var studentName: String? = null

    @Column(name = "user_email", nullable = false, length = 255)
    var userEmail: String = ""

    @Column(name = "company", nullable = false, length = 120)
    var company: String = ""

    @Column(name = "role", length = 120)
    var role: String? = null

    /** Placement | Internship | PPO */
    @Column(name = "offer_type", nullable = false, length = 20)
    var offerType: String = "Placement"

    /** Selected | Not selected | Awaiting result */
    @Column(name = "verdict", nullable = false, length = 20)
    var verdict: String = "Selected"

    /** LINK | TEXT | FILE */
    @Column(name = "format", nullable = false, length = 10)
    var format: String = "TEXT"

    @Column(name = "link", length = 1000)
    var link: String? = null

    @Column(name = "body", columnDefinition = "text")
    var body: String? = null

    @Column(name = "file_name", length = 255)
    var fileName: String? = null

    /** PENDING until reviewed; then PUBLISHED or REJECTED. */
    @Column(name = "status", nullable = false, length = 20)
    var status: String = "PENDING"
}
