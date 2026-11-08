package com.attendanceio.api.model.poll

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "poll")
class DMPoll : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: DMSubject? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    var createdBy: DMStudent? = null

    @Column(name = "title", nullable = false, length = 255)
    var title: String = ""

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_type", nullable = false, length = 32)
    var pollType: PollType = PollType.SINGLE_CHOICE

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: PollStatus = PollStatus.ACTIVE

    @Column(name = "end_date")
    var endDate: LocalDateTime? = null

    @Column(name = "report_count", nullable = false)
    var reportCount: Int = 0

    @Column(name = "is_flagged_spam", nullable = false)
    var isFlaggedSpam: Boolean = false

    @OneToMany(mappedBy = "poll", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    var options: MutableList<DMPollOption> = mutableListOf()
}

@Entity
@Table(name = "poll_option")
class DMPollOption : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    var poll: DMPoll? = null

    @Column(name = "option_text", nullable = false, length = 255)
    var optionText: String = ""

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    @Column(name = "vote_count", nullable = false)
    var voteCount: Int = 0
}

@Entity
@Table(name = "poll_vote")
class DMPollVote : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    var poll: DMPoll? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    var option: DMPollOption? = null
}

@Entity
@Table(name = "poll_report")
class DMPollReport : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    var poll: DMPoll? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 64)
    var reason: PollReportReason = PollReportReason.SPAM

    @Column(name = "details", length = 500)
    var details: String? = null

    @Column(name = "report_author_as_spammer", nullable = false)
    var reportAuthorAsSpammer: Boolean = false
}
