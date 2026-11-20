package com.attendanceio.api.repository.notice

import com.attendanceio.api.model.notice.DMUrgentNotice
import com.attendanceio.api.model.notice.DMUrgentNoticeReport
import com.attendanceio.api.model.notice.DMUrgentNoticeSeen
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface UrgentNoticeRepository : JpaRepository<DMUrgentNotice, Long> {
    @Query("""
        SELECT n FROM DMUrgentNotice n
        WHERE n.subject.id IN :subjectIds
          AND n.createdAt >= :since
          AND n.hidden = false
          AND n.deletedAt IS NULL
        ORDER BY n.createdAt DESC
    """)
    fun findActiveForSubjects(
        @Param("subjectIds") subjectIds: Collection<Long>,
        @Param("since") since: LocalDateTime
    ): List<DMUrgentNotice>

    @Query("""
        SELECT n FROM DMUrgentNotice n
        WHERE n.subject.id = :subjectId
          AND n.createdAt >= :since
          AND n.hidden = false
          AND n.deletedAt IS NULL
        ORDER BY n.createdAt DESC
    """)
    fun findActiveForSubject(
        @Param("subjectId") subjectId: Long,
        @Param("since") since: LocalDateTime
    ): List<DMUrgentNotice>

    @Query("""
        SELECT COUNT(n) FROM DMUrgentNotice n
        WHERE n.creator.id = :studentId
          AND n.createdAt >= :since
    """)
    fun countCreatedSince(
        @Param("studentId") studentId: Long,
        @Param("since") since: LocalDateTime
    ): Long

    @Query("""
        SELECT COUNT(n) FROM DMUrgentNotice n
        WHERE n.creator.id = :studentId
          AND n.hidden = true
    """)
    fun countHiddenBy(@Param("studentId") studentId: Long): Long
}

@Repository
interface UrgentNoticeReportRepository : JpaRepository<DMUrgentNoticeReport, Long> {
    fun existsByNoticeIdAndStudentId(noticeId: Long, studentId: Long): Boolean
    fun countByNoticeId(noticeId: Long): Long
}

@Repository
interface UrgentNoticeSeenRepository : JpaRepository<DMUrgentNoticeSeen, Long> {
    fun existsByNoticeIdAndStudentId(noticeId: Long, studentId: Long): Boolean

    @Query("SELECT s.notice.id FROM DMUrgentNoticeSeen s WHERE s.student.id = :studentId AND s.notice.id IN :noticeIds")
    fun findSeenNoticeIds(
        @Param("studentId") studentId: Long,
        @Param("noticeIds") noticeIds: Collection<Long>
    ): List<Long>
}
