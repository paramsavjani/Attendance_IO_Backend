package com.attendanceio.api.model.notice

import com.attendanceio.api.model.BaseEntity
import com.attendanceio.api.model.student.DMStudent
import com.attendanceio.api.model.subject.DMSubject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "urgent_notice",
    indexes = [
        Index(name = "idx_notice_subject_created", columnList = "subject_id, created_at"),
        Index(name = "idx_notice_creator_created", columnList = "creator_id, created_at")
    ]
)
class DMUrgentNotice : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: DMSubject? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    var creator: DMStudent? = null

    @Column(name = "creator_name", length = 100)
    var creatorName: String? = null

    @Column(name = "creator_sid", length = 50)
    var creatorSid: String? = null

    @Column(name = "title", nullable = false, length = 100)
    var title: String = ""

    @Column(name = "message", nullable = false, length = 300)
    var message: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 32)
    var noticeType: NoticeType = NoticeType.CANCELLED_LECTURE

    @Column(name = "report_count", nullable = false)
    var reportCount: Int = 0

    @Column(name = "hidden", nullable = false)
    var hidden: Boolean = false

    @Column(name = "deleted_at")
    var deletedAt: java.time.LocalDateTime? = null
}

@Entity
@Table(
    name = "urgent_notice_report",
    indexes = [
        Index(name = "idx_notice_report_notice_student", columnList = "notice_id, student_id", unique = true)
    ]
)
class DMUrgentNoticeReport : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id", nullable = false)
    var notice: DMUrgentNotice? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null

    @Column(name = "reason", length = 200)
    var reason: String? = null
}

@Entity
@Table(
    name = "urgent_notice_seen",
    indexes = [
        Index(name = "idx_notice_seen_notice_student", columnList = "notice_id, student_id", unique = true)
    ]
)
class DMUrgentNoticeSeen : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id", nullable = false)
    var notice: DMUrgentNotice? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: DMStudent? = null
}
